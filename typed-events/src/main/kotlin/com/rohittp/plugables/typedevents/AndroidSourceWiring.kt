package com.rohittp.plugables.typedevents

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * Kept outside [TypedEventsPlugin] so a pure KMP consumer can instantiate the plugin without AGP
 * on its runtime classpath. This class is loaded only after an Android plugin has been applied.
 */
internal object AndroidSourceWiring {
    fun wire(project: Project, generateTask: TaskProvider<GenerateTypedEventsTask>) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.onVariants { variant ->
            variant.sources.java?.addGeneratedSourceDirectory(
                generateTask,
                GenerateTypedEventsTask::outputDir,
            )
            variant.sources.kotlin?.addGeneratedSourceDirectory(
                generateTask,
                GenerateTypedEventsTask::outputDir,
            )
        }
    }
}
