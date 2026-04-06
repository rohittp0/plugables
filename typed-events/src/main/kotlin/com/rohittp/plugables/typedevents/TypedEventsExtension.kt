package com.rohittp.plugables.typedevents

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import javax.inject.Inject

abstract class TypedEventsExtension @Inject constructor(layout: ProjectLayout) {

    abstract val specFile: RegularFileProperty

    abstract val outputDir: DirectoryProperty

    init {
        outputDir.convention(layout.buildDirectory.dir("generated/source/typedEvents/main"))
    }
}
