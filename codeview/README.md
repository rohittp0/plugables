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
    id("com.rohittp.plugables.codeview") version "1.3.0"
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
    id("com.rohittp.plugables.codeview") version "1.3.0"
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
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
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
- **Detail view** — clicking a card navigates to `#preview-id` and shows the screenshot at full resolution. A sidebar lists every other preview (one click to switch) and an **← All previews** link returns to the grid. URLs are shareable; browser back/forward work.
- **Hover tooltip** — every clickable overlay shows a tooltip with the composable name, the file:line label, and the actual line of Kotlin source from your project. Click the overlay to deep-link into your IDE at that exact line.

## Why this is the way it is

Five non-obvious things forced the current shape of the plugin; document them so the next person doesn't have to relearn:

1. **The unit-test APK uses your main `AndroidManifest.xml`, not the merged unit-test one.** AGP packages `apk-for-local-test.ap_` from the main source set; entries you put under `src/test/AndroidManifest.xml` never reach Robolectric's `PackageManager`. That's why `testActivityClass` must point at an activity already registered in main.
2. **Robolectric PR #4736 enforces strict activity resolution.** Even with an explicit component (`cmp=...`) in the launch intent, it requires the activity at that component to have a matching `MAIN/LAUNCHER` intent filter. Hence the recommendation above to add such a filter to whichever activity codeview launches.
3. **`LocalInspectionTables` alone doesn't populate.** You also have to add `currentComposer.compositionData` to the set inside the composition itself — that's the same pattern Layout Inspector uses internally.
4. **AGP uninstalls the app after `connected*AndroidTest`.** Anything written to the app's `externalCacheDir` is gone before the host can `adb pull` it. The instrumented helper republishes sidecars to `/sdcard/codeview-sidecars/` from inside the test process, using `UiAutomation.executeShellCommand` (uid 2000, has write access to `/sdcard`).
5. **`adb` is rarely on Gradle's `PATH`.** The pull task resolves it from `local.properties#sdk.dir`, then `ANDROID_HOME`, then `ANDROID_SDK_ROOT`, falling back to `adb` on the path as a last resort.

## Configuration notes

- **Source directories.** `sourceDirs` defaults to both `src/main/kotlin` and `src/main/java` so the standard Android Studio layout (Kotlin under `src/main/java`) works out of the box. Override it explicitly if your previews live elsewhere:
  ```kotlin
  codeview {
      sourceDirs.from(layout.projectDirectory.dir("src/main/java"))
  }
  ```
- **Private `@Preview` functions are skipped.** Top-level `private fun` in Kotlin is file-scoped, so generated test files in another file can't invoke them. Codeview logs a warning listing every skipped FQN — change them to `internal fun` (or drop the modifier) to include them in the report.
- **Preview ids are stable across runs and unique across files.** The id format is `Codeview_<funName>_<8-hex>` where the hex disambiguator is derived from the source file path and the preview FQN. Two `@Preview fun MyPreview()` declared in different files no longer collide on the test class name or sidecar file.
- **Incremental rendering (v1.2+).** Each sidecar JSON stores a SHA-256 of its source file. On subsequent runs, codeview's batched test skips previews whose hash matches the previous run, so the device avoids the expensive `captureToImage` step. Granularity is per `.kt` file: editing any preview in a file re-renders all previews in that file. Cross-file dependencies (themes, shared composables, resources) aren't tracked — use `./gradlew :app:codeviewReportDebug --rerun-tasks` to force a full re-render when they change.
- **Batched rendering (v1.3+).** Codeview now generates a single `CodeviewBatchTest` with one `@Test fun renderAll()` that loops over a registry of every preview in the module, instead of one test class per preview. The Activity launches once, the Compose runtime initialises once, and unchanged previews are filtered inside the loop via a `SKIPPED_IDS` set. This removes the per-test `Activity.onCreate` + Compose-init overhead that previously dominated reruns. Trade-off: JUnit reports show one test row instead of N, and a render exception in one preview no longer fails an isolated JUnit row — the failure is recorded in that preview's sidecar (`renderError` field, schema 3) and surfaced on the report card. Other previews in the batch keep going.

## v1 limitations

- **`unit` mode emits no PNGs.** `captureToImage()` times out under Robolectric's headless graphics mode. Use `testMode = "instrumented"` if you need bitmaps.
- **Source-line precision** stops at what Compose's `sourceInformation` records — typically the call site of each composable. Lambda contents (e.g. inside `Text("foo")`) resolve to the `Text(...)` call site.
- Only JetBrains `idea://` and VS Code `vscode://` URL schemes are supported. Browser must register the protocol handler.
- `androidx.compose.ui.tooling.data` is `@UiToolingDataApi` (experimental). Bumping Compose may require codeview to follow.
- `@PreviewParameter` variants render as separate sequenced entries (`_0`, `_1`, …).
- Instrumented mode requires a device/emulator at run time; CI has to provision one (managed devices, GMD, or a separate emulator step).

## Future work

- Discover the user's existing `MAIN/LAUNCHER` activity automatically (avoid the manual `testActivityClass` config).
- Use `androidx.test.services.storage.TestStorage` + the test orchestrator to replace the `/sdcard` republish hack.
- Multi-IDE URL switcher in the report UI (currently scheme is fixed at build time).
- Multi-module aggregation into a single index.
