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
     * Fully-qualified name of an Activity (extending ComponentActivity) registered in the user's
     * MAIN AndroidManifest with a `MAIN`/`LAUNCHER` intent filter. The generated unit tests
     * launch this activity to host previews. Required because AGP's unit-test APK uses the main
     * manifest, and Robolectric (since PR #4736) refuses to resolve activities not registered
     * with a matching intent filter.
     */
    abstract val testActivityClass: Property<String>

    init {
        sourceDirs.from(layout.projectDirectory.dir("src/main/kotlin"))
        outputDir.convention(layout.buildDirectory.dir("reports/codeview"))
        ideScheme.convention("idea")
    }
}
