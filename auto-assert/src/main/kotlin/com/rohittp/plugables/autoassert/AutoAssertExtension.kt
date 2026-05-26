package com.rohittp.plugables.autoassert

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import javax.inject.Inject

abstract class AutoAssertExtension @Inject constructor(layout: ProjectLayout) {

    abstract val outputDir: DirectoryProperty

    init {
        outputDir.convention(layout.buildDirectory.dir("generated/source/autoAssert/main"))
    }
}
