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

    init {
        sourceDirs.from(layout.projectDirectory.dir("src/main/kotlin"))
        outputDir.convention(layout.buildDirectory.dir("reports/codeview"))
        ideScheme.convention("idea")
    }
}
