# CodeView

Generate an interactive HTML report of every `@Preview` composable in your Android module, with click-to-IDE deep-links **per composable**.

## How it works

For each `@Preview` discovered in your sources, codeview generates a JUnit test that renders the preview under Compose UI testing, walks the slot table via `androidx.compose.ui.tooling.data` (the same API Layout Inspector uses) to extract every composable's bounds + source location, captures a bitmap, and writes a JSON sidecar to `build/generated/codeview/sidecars/`. A second task assembles the sidecars into a single `index.html`.

It uses Compose's existing `sourceInformation` metadata (default-on for debug builds) — no custom compiler plugin.

Two test modes are supported via the `testMode` DSL knob:

| Mode | Runner | Speed | PNG capture | Requires |
|---|---|---|---|---|
| `unit` (default) | Robolectric on JVM | Fast (~1 s/preview) | No (Robolectric `WindowCapture` times out) | JDK only |
| `instrumented` | AndroidJUnit4 on device | Slower (~3–5 s/preview) | **Yes**, real RGBA bitmaps | Connected device or emulator + `adb` on the SDK |

## Common setup

You need an `Activity` registered in your **main** `AndroidManifest.xml` with a `MAIN/LAUNCHER` intent filter that does **not** populate any content in `onCreate()` (codeview owns the content via Compose UI testing). The simplest option is to declare `androidx.activity.ComponentActivity` directly:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <application>
        <!-- Your real launcher activity -->
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Hosting activity for codeview test renders -->
        <activity
            android:name="androidx.activity.ComponentActivity"
            android:exported="true"
            tools:replace="android:exported">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

## Mode 1 — unit (Robolectric, default)

`build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.rohittp.plugables.codeview") version "1.3.1"
}

android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.useJUnit() }
        }
    }
}

dependencies {
    testImplementation(platform("androidx.compose:compose-bom:2026.04.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4-android")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("androidx.compose.ui:ui-tooling-data")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("junit:junit:4.13.2")
}

codeview {
    ideScheme.set("idea") // or "vscode"
    testActivityClass.set("androidx.activity.ComponentActivity")
    // testMode defaults to "unit"
}
```

Run: `./gradlew :app:codeviewReportDebug`. The report will list every preview with its full slot tree as overlays, but the `<img>` slot is empty — Robolectric's `WindowCapture.forceRedraw` doesn't complete in headless mode, so codeview catches the timeout and continues with metadata only.

## Mode 2 — instrumented (real device, real PNGs)

`build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.rohittp.plugables.codeview") version "1.3.1"
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4-android")
    androidTestImplementation("androidx.compose.ui:ui-tooling-data")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test:monitor:1.7.2")  // see note below
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

codeview {
    ideScheme.set("idea") // or "vscode"
    testActivityClass.set("androidx.activity.ComponentActivity")
    testMode.set("instrumented")
}
```

> **Note on `androidx.test:monitor`.** `ActivityScenario` (used internally by `runAndroidComposeUiTest`) needs `androidx.test.internal.platform.app.ActivityInvoker`, which lives in `androidx.test:monitor`. This is normally pulled transitively by `androidx.test:core` / `:runner`. **AGP 9.x with the test orchestrator** (`testOptions { execution = "ANDROIDX_TEST_ORCHESTRATOR" }`) strips monitor's classes out of the test APK on the assumption orchestrator provides them — which only holds for orchestrator-managed runs, not for codeview's plain `AndroidJUnitRunner` execution. Symptom: `NoClassDefFoundError: ActivityInvoker` at activity launch. If you hit this, either drop orchestrator for the codeview run, or stop AGP from filtering monitor by depending on it as a flat jar (`androidTestImplementation(files("libs/monitor-X.Y.Z-classes.jar"))`).

Make sure an emulator or device is connected (`adb devices` shows it), then run:

```bash
./gradlew :app:codeviewReportDebug
```

The pipeline runs `connected{Variant}AndroidTest`, the tests publish sidecars to `/sdcard/codeview-sidecars/` via `UiAutomation.executeShellCommand` (so they survive AGP's post-test app uninstall), `pullCodeviewSidecars{Variant}` `adb pull`s them, and the report task assembles the HTML. Verified end-to-end on Pixel 9 Pro XL (API 35) with 731×194 and 549×194 RGBA PNGs.

## Usage

Either mode runs as a single command:

```bash
./gradlew :app:codeviewReportDebug
```

The task logs the absolute path and a `file://` URL of the generated `index.html` on completion, and (by default) opens the report in your default browser via `open` / `xdg-open` / `cmd /c start`. Disable that with `codeview { openOnComplete.set(false) }` in CI.

## What you get in the HTML

- **Home** — a responsive grid of cards (one per `@Preview`), each showing the rendered screenshot, the preview name, and a node count.
- **Search** — the search box on the home page filters by preview name, slot-tree node names, and the *rendered* text strings collected from Compose's semantics tree. So if your code says `Text(stringResource(R.string.welcome))` and the resolved value is "Welcome back", searching `welcome back` finds the preview.
- **Detail view** — clicking a card navigates to `#preview-id` and shows the screenshot. A sidebar lists every other preview (one click to switch) and an **← All previews** link returns to the grid. URLs are shareable; browser back/forward work.
- **Zoom controls** — each detail view has a toolbar above the screenshot with `Fit` (default; fits to the viewport), `1:1`, `+` and `−` buttons. The viewport scrolls when zoomed past fit so you can pan around large screens. Bounding boxes scale with the image.
- **Hover overlays** — bounding boxes are invisible by default and outlined on hover. **Source-backed overlays** (your composables) are blue and link into your IDE at the exact line on click. **Library/internal overlays** (Material, Foundation, Compose internals — they don't carry source attribution) are gray, hover-only, non-clickable. The hover tooltip shows the composable name, file:line, and the actual line of Kotlin source.

## Why this is the way it is

Several non-obvious things forced the current shape of the plugin; document them so the next person doesn't have to relearn:

1. **The unit-test APK uses your main `AndroidManifest.xml`, not the merged unit-test one.** AGP packages `apk-for-local-test.ap_` from the main source set; entries you put under `src/test/AndroidManifest.xml` never reach Robolectric's `PackageManager`. That's why `testActivityClass` must point at an activity already registered in main.
2. **Robolectric PR #4736 enforces strict activity resolution.** Even with an explicit component (`cmp=...`) in the launch intent, it requires the activity at that component to have a matching `MAIN/LAUNCHER` intent filter. Hence the recommendation above to add such a filter to whichever activity codeview launches.
3. **`LocalInspectionTables` alone doesn't populate.** You also have to add `currentComposer.compositionData` to the set inside the composition itself — that's the same pattern Layout Inspector uses internally.
4. **`LocalInspectionTables` doesn't propagate into subcompositions.** `LazyVerticalGrid`, `LazyRow`, `SubcomposeLayout`, etc. each create a separate composition that does **not** register with our `Inspectable`'s tables. The slot-tree walk would then only see one item per lazy container. Codeview compensates with a secondary walk of the unmerged semantics tree (`onAllNodes(isRoot(), useUnmergedTree = true)`) that *does* see every laid-out child, and emits a synthetic bounding box per item that mapTree missed. These boxes don't carry source-line info (semantics doesn't record it), so they render as the gray non-clickable variant.
5. **Dialog/Popup/BottomSheet previews mount a second window.** The activity's main compose root ends up at 0×0 because content lives in the dialog's window. `onRoot()` would throw `Expected exactly '1' node but found '2'`. The runtime instead enumerates all roots and picks the **largest by area**, which targets the window with the actual content.
6. **An unhandled throw inside a composable cancels the Compose `Recomposer`.** Compose forbids `try/catch` around composable invocations, so we can't recover in-line. Once the Recomposer is cancelled, every subsequent preview in the batch fails with `IllegalStateException: No compose hierarchies found in the app`. Use `excludePreviews` (see below) to skip previews that fundamentally can't render in the test environment — e.g. a composable that calls `hiltViewModel()` against a plain `ComponentActivity` host. The cascade then doesn't start.
7. **AGP uninstalls the app after `connected*AndroidTest`.** Anything written to the app's `externalCacheDir` is gone before the host can `adb pull` it. The instrumented helper republishes sidecars to `/sdcard/codeview-sidecars/` from inside the test process, using `UiAutomation.executeShellCommand` (uid 2000, has write access to `/sdcard`). It also drops a `__codeview_published__` sentinel into that dir so the host pull task can tell *this run's* output from a previous run's leftovers — if the sentinel isn't present, the pull task fails the build instead of silently re-publishing stale data.
8. **`adb` is rarely on Gradle's `PATH`.** The pull task resolves it from `local.properties#sdk.dir`, then `ANDROID_HOME`, then `ANDROID_SDK_ROOT`, falling back to `adb` on the path as a last resort.

## Configuration notes

- **Source directories.** `sourceDirs` defaults to both `src/main/kotlin` and `src/main/java` so the standard Android Studio layout (Kotlin under `src/main/java`) works out of the box. Override it explicitly if your previews live elsewhere:
  ```kotlin
  codeview {
      sourceDirs.from(layout.projectDirectory.dir("src/main/java"))
  }
  ```
- **Excluding previews that can't render.** Use `excludePreviews` to skip individual `@Preview` functions by display name. Excluded previews are filtered out at source-parse time, never enter the registry, never execute, and don't appear in the report. Use this for previews that fundamentally can't render in codeview's test environment — most commonly composables that call `hiltViewModel()` against the plain-`ComponentActivity` host (the throw from Hilt would otherwise cancel the Compose `Recomposer` and cascade-fail every later preview in the batch with "No compose hierarchies found"):
  ```kotlin
  codeview {
      excludePreviews.add("HomeScreenPreview")
      excludePreviews.add("OnboardingScreenPreview")
  }
  ```
- **Private `@Preview` functions are skipped.** Top-level `private fun` in Kotlin is file-scoped, so generated test files in another file can't invoke them. Codeview logs a warning listing every skipped FQN — change them to `internal fun` (or drop the modifier) to include them in the report.
- **Preview ids are stable across runs and unique across files.** The id format is `Codeview_<funName>_<8-hex>` where the hex disambiguator is derived from the source file path and the preview FQN. Two `@Preview fun MyPreview()` declared in different files no longer collide on the test class name or sidecar file.
- **Incremental rendering (v1.2+).** Each sidecar JSON stores a SHA-256 of its source file. On subsequent runs, codeview's batched test skips previews whose hash matches the previous run, so the device avoids the expensive `captureToImage` step. Granularity is per `.kt` file: editing any preview in a file re-renders all previews in that file. Cross-file dependencies (themes, shared composables, resources) aren't tracked — use `./gradlew :app:codeviewReportDebug --rerun-tasks` *and* delete `app/build/generated/codeview/sidecars/` to force a full re-render when they change. (`--rerun-tasks` alone re-runs the tasks but the skip set is computed from existing sidecar files.)
- **Skip-set is conservative (v1.3+).** A previous-run sidecar is reused only if it carries actually-usable data: matching `sourceHash`, no `renderError`, `imageWidth > 0`, `imageHeight > 0`, and a non-empty `nodes` array. Silent capture failures (e.g. a 0×0 preview that wrote a sidecar with no bitmap) are *not* latched into the skip set on the next run.
- **Batched rendering (v1.3+).** Codeview now generates a single `CodeviewBatchTest` with one `@Test fun renderAll()` that loops over a registry of every preview in the module, instead of one test class per preview. The Activity launches once, the Compose runtime initialises once, and unchanged previews are filtered inside the loop via a `SKIPPED_IDS` set. This removes the per-test `Activity.onCreate` + Compose-init overhead that previously dominated reruns. Trade-off: JUnit reports show one test row instead of N, and a render exception in one preview no longer fails an isolated JUnit row — the failure is recorded in that preview's sidecar (`renderError` field, schema 3) and surfaced on the report card. Other previews in the batch keep going. The default `runAndroidComposeUiTest` timeout is bumped to 30 minutes (1 minute is too tight for batches of ~50+ previews on real devices).

## Troubleshooting

- **`pullCodeviewSidecars{Variant}` fails with "No sentinel `__codeview_published__` on device"** — the instrumented test process never reached `CodeviewRuntime.runBatch`. Check the AGP-captured logcat under `app/build/outputs/androidTest-results/connected/<variant>/<device>/logcat-*.txt` for the underlying crash. The pull task refuses to publish a report from stale sidecars on purpose.
- **`NoClassDefFoundError: androidx.test.internal.platform.app.ActivityInvoker`** when the test launches — `androidx.test:monitor` isn't landing in the test APK. Most common cause is **AGP 9.x + the test orchestrator** (`testOptions { execution = "ANDROIDX_TEST_ORCHESTRATOR" }` and/or `androidTestUtil(...orchestrator)`): AGP filters monitor's classes out of `mergeExtDexDebugAndroidTest` on the assumption orchestrator provides them at runtime, but that's not true for plain `AndroidJUnitRunner` + `ActivityScenario`. Either disable orchestrator for the codeview test, or add `androidx.test:monitor` as a flat-jar file dep (`androidTestImplementation(files("libs/monitor-X.Y.Z-classes.jar"))`) so AGP can't strip it.
- **Most/all previews end up with "No compose hierarchies found in the app"** — one preview earlier in the batch threw inside its composable and cancelled the Compose `Recomposer`; the rest are collateral damage. Find the first failing preview in the build's sidecar JSON (look for the first non-null `renderError` in batch order) and add it to `excludePreviews`. The most common offenders are previews that call `hiltViewModel()` against the plain `ComponentActivity` host.
- **Preview rendered at 0×0** in `renderError` — the preview itself produced no laid-out content. Either wrap it in a sized container (`Box(Modifier.size(360.dp, 640.dp)) { … }`) or pin the size on the annotation (`@Preview(widthDp = 360, heightDp = 640)`).
- **Bounding boxes for lazy-grid items are missing** — codeview captures these via a secondary semantic-tree walk, which only emits if the items are actually laid out at render time. Lazy layouts in inspection mode sometimes only compose the first visible cell; if your preview is meaningfully smaller than the grid's content, set the grid's height explicitly so all rows fit.

## v1 limitations

- **`unit` mode emits no PNGs.** `captureToImage()` times out under Robolectric's headless graphics mode. Use `testMode = "instrumented"` if you need bitmaps.
- **Source-line precision** stops at what Compose's `sourceInformation` records — typically the call site of each composable. Lambda contents (e.g. inside `Text("foo")`) resolve to the `Text(...)` call site. Items inside `LazyRow`/`LazyVerticalGrid`/`SubcomposeLayout` show up as gray non-clickable boxes (semantics doesn't carry source info).
- Only JetBrains `idea://` and VS Code `vscode://` URL schemes are supported. Browser must register the protocol handler.
- `androidx.compose.ui.tooling.data` is `@UiToolingDataApi` (experimental). Bumping Compose may require codeview to follow.
- `@PreviewParameter` variants render as separate sequenced entries (`_0`, `_1`, …).
- Instrumented mode requires a device/emulator at run time; CI has to provision one (managed devices, GMD, or a separate emulator step).
- **Hilt-aware test activity not yet supported.** Composables that call `hiltViewModel()` need a `HiltTestActivity` host with `@HiltAndroidTest` plumbing; codeview only generates plain `runAndroidComposeUiTest(activityClass = ...)` blocks. For now, exclude such previews via `excludePreviews`. Native Hilt support is on the roadmap.

## Future work

- Discover the user's existing `MAIN/LAUNCHER` activity automatically (avoid the manual `testActivityClass` config).
- Use `androidx.test.services.storage.TestStorage` + the test orchestrator to replace the `/sdcard` republish hack.
- Multi-IDE URL switcher in the report UI (currently scheme is fixed at build time).
- Multi-module aggregation into a single index.
