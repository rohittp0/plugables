package com.rohittp.plugables.codeview

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class CodeViewExtension @Inject constructor(layout: ProjectLayout) {

    abstract val sourceDirs: ConfigurableFileCollection

    abstract val outputDir: DirectoryProperty

    abstract val ideScheme: Property<String>

    /**
     * Fully-qualified name of an Activity (extending ComponentActivity) registered in the
     * user's MAIN AndroidManifest with a `MAIN`/`LAUNCHER` intent filter. The generated tests
     * launch this activity to host previews. Required because AGP's unit-test APK uses the
     * main manifest (so test-only entries are ignored), and Robolectric (PR #4736) refuses to
     * resolve activities not registered with a matching intent filter.
     */
    abstract val testActivityClass: Property<String>

    /**
     * Test execution mode:
     *  - `"unit"` (default): generate Robolectric-based unit tests; runs on JVM, fast, but
     *    `captureToImage()` times out so the report has no PNGs.
     *  - `"instrumented"`: generate AndroidJUnit4 instrumented tests; runs on device/emulator,
     *    captures real bitmaps. Codeview pulls the on-device sidecars via `adb` after the
     *    `connected{Variant}AndroidTest` task finishes.
     */
    abstract val testMode: Property<String>

    init {
        sourceDirs.from(layout.projectDirectory.dir("src/main/kotlin"))
        outputDir.convention(layout.buildDirectory.dir("reports/codeview"))
        ideScheme.convention("idea")
        testMode.convention("unit")
    }
}
