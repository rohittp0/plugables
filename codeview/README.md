# CodeView

Generate an interactive HTML report of every `@Preview` composable in your Android module, with click-to-IDE deep-links **per composable**.

## How it works

For each `@Preview` discovered in your sources, codeview generates a JUnit test that renders the preview under Robolectric + Compose UI testing, walks the slot table to extract every composable's bounds + source location, captures a bitmap, and writes both to `build/generated/codeview/sidecars/`. A second task assembles the sidecars into a single `index.html`.

It uses Compose's existing `sourceInformation` metadata (default-on for debug builds) — no custom compiler plugin.

## Setup

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

codeview { ideScheme.set("idea") } // or "vscode"
```

You also need a unit-test manifest at `src/test/AndroidManifest.xml` adding the launcher intent filter to `ComponentActivity` (workaround for Robolectric/ComposeUiTest compatibility, see Limitations):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <application>
        <activity
            android:name="androidx.activity.ComponentActivity"
            android:exported="true"
            tools:node="merge">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

## Usage

```bash
./gradlew testDebugUnitTest        # renders previews + extracts slot tree
./gradlew codeviewReportDebug      # assembles HTML report
open app/build/reports/codeview/debug/index.html
```

## v1 limitations

- **Robolectric/ComposeUiTest interop is currently broken** at activity launch in the version combination targeted (Robolectric 4.16.1 + Compose UI test 1.11). Robolectric PR #4736 added a strict activity-resolution check that rejects `ActivityScenario.launch(ComponentActivity::class)` when the registered activity has no matching intent filter — even though the launch intent has the component explicitly set. The test manifest workaround above adds a `MAIN/LAUNCHER` intent filter to `ComponentActivity` for unit tests; resolution is still flaky depending on Robolectric/Compose versions in your project. Until upstream resolves this, runtime rendering of previews under Robolectric may fail. Sidecar generation, the report task, and the HTML pipeline all work; only the actual unit-test render step is blocked. Switching to instrumented tests (which need an emulator) sidesteps this entirely.
- **Source-line precision** stops at what Compose's `sourceInformation` records — typically the call site of each composable. Lambda contents (e.g. inside `Text("foo")`) resolve to the `Text(...)` call site.
- Only JetBrains `idea://` and VS Code `vscode://` URL schemes are supported. Browser must register the protocol handler.
- `androidx.compose.ui.tooling.data` is `@UiToolingDataApi` (experimental). Bumping Compose may require codeview to follow.
- `@PreviewParameter` variants render as separate sequenced entries (`_0`, `_1`, …).
- Robolectric tests are slower than the Layoutlib screenshot path — large modules pay a per-test second.

## Status

v1 is **partial**: discovery, test generation, slot-tree extraction logic, JSON sidecar format, and HTML report are complete. The runtime render step is blocked on the upstream Robolectric/Compose-UI-test incompatibility above. Future work: switch to a Layoutlib-based renderer (would re-introduce the screenshot plugin), or wait for upstream resolution.
