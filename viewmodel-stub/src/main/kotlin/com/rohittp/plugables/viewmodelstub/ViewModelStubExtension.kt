package com.rohittp.plugables.viewmodelstub

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import javax.inject.Inject

abstract class ViewModelStubExtension @Inject constructor(layout: ProjectLayout) {

    abstract val sourceDir: DirectoryProperty
    abstract val outputDir: DirectoryProperty

    init {
        sourceDir.convention(layout.projectDirectory.dir("src/main/java"))
        outputDir.convention(layout.buildDirectory.dir("generated/source/viewModelStubs/main"))
    }
}
