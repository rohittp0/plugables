# CodeView

Generate an interactive HTML report of every `@Preview` composable in your Android module, with click-to-IDE deep-links **per composable**.

## How it works

For each `@Preview` discovered in your sources, codeview generates a JUnit test that renders the preview under Robolectric + Compose UI testing, walks the slot table via `androidx.compose.ui.tooling.data` (the same API Layout Inspector uses) to extract every composable's bounds + source location, and writes a JSON sidecar to `build/generated/codeview/sidecars/`. A second task assembles the sidecars into a single `index.html`.

It uses Compose's existing `sourceInformation` metadata (default-on for debug builds) — no custom compiler plugin.

## Setup

You need an `Activity` registered in your **main** `AndroidManifest.xml` with a `MAIN/LAUNCHER` intent filter that does **not** populate any content in `onCreate()` (codeview will own its content via Compose UI testing). The simplest option is to declare `androidx.activity.ComponentActivity` directly:

```xml
<application ...>
    <!-- Your real launcher activity -->
    <activity android:name=".MainActivity" android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>

    <!-- Hosting activity for codeview test renders -->
    <activity
        xmlns:tools="http://schemas.android.com/tools"
        android:name="androidx.activity.ComponentActivity"
        android:exported="true"
        tools:replace="android:exported">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

`build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.rohittp.plugables.codeview") version "0.1.0"
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
}
```

## Usage

```bash
./gradlew :app:codeviewReportDebug
```

That single task runs the generated unit tests (which extract the slot tree and write per-preview sidecars), then assembles the HTML report. The task logs the path to the generated `index.html` on completion — open it in any browser.

## Why this is the way it is

Three non-obvious things forced the current shape of the plugin; document them so the next person doesn't have to relearn:

1. **The unit-test APK uses your main `AndroidManifest.xml`, not the merged unit-test one.** AGP packages `apk-for-local-test.ap_` from the main source set; entries you put under `src/test/AndroidManifest.xml` never reach Robolectric's `PackageManager`. That's why `testActivityClass` must point at an activity already registered in main.
2. **Robolectric PR #4736 enforces strict activity resolution.** Even with an explicit component (`cmp=...`) in the launch intent, it requires the activity at that component to have a matching `MAIN/LAUNCHER` intent filter. Hence the recommendation above to add such a filter to whichever activity codeview launches.
3. **`LocalInspectionTables` alone doesn't populate.** You also have to add `currentComposer.compositionData` to the set inside the composition itself — that's the same pattern Layout Inspector uses internally.

## v1 limitations

- **PNG capture under Robolectric is unreliable.** `captureToImage()` times out in `WindowCapture.forceRedraw` under Robolectric's headless graphics mode. Codeview catches the timeout and continues without the bitmap; the JSON sidecar (with bounds and source positions) is still emitted, and the HTML report degrades gracefully to "no screenshot". To get the PNGs, switch to instrumented tests (needs an emulator) — the same generated test classes work there.
- **Source-line precision** stops at what Compose's `sourceInformation` records — typically the call site of each composable. Lambda contents (e.g. inside `Text("foo")`) resolve to the `Text(...)` call site.
- Only JetBrains `idea://` and VS Code `vscode://` URL schemes are supported. Browser must register the protocol handler.
- `androidx.compose.ui.tooling.data` is `@UiToolingDataApi` (experimental). Bumping Compose may require codeview to follow.
- `@PreviewParameter` variants render as separate sequenced entries (`_0`, `_1`, …).
- Robolectric tests are slower than the Layoutlib screenshot path — large modules pay a per-test second.

## Future work

- Restore PNG capture via a Layoutlib renderer (would re-introduce the `com.android.compose.screenshot` plugin path) or via instrumented tests on CI.
- Discover the user's existing `MAIN/LAUNCHER` activity automatically (avoid the manual `testActivityClass` config).
- Multi-IDE URL switcher in the report UI (currently scheme is fixed at build time).
- Multi-module aggregation into a single index.
