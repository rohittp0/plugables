package com.rohittp.plugables.typedevents

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

class TypedEventsPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create("typedEvents", TypedEventsExtension::class.java)

        val generateTask = project.tasks.register(
            "generateTypedEvents",
            GenerateTypedEventsTask::class.java,
            Action {
                specFile.set(ext.specFile)
                outputDir.set(ext.outputDir)
            }
        )

        // Wire before any Kotlin compile task — lazy, no afterEvaluate needed
        project.tasks.matching { t -> t.name.matches(Regex("compile.*Kotlin")) }
            .configureEach(Action { dependsOn(generateTask) })

        // Wire Android generated sources using the Variant API (AGP 7.2+)
        project.plugins.withId("com.android.application") {
            wireAndroidSources(project, generateTask)
        }
        project.plugins.withId("com.android.library") {
            wireAndroidSources(project, generateTask)
        }
    }

    private fun wireAndroidSources(project: Project, generateTask: TaskProvider<GenerateTypedEventsTask>) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.onVariants { variant ->
            variant.sources.java?.addGeneratedSourceDirectory(generateTask, GenerateTypedEventsTask::outputDir)
            variant.sources.kotlin?.addGeneratedSourceDirectory(generateTask, GenerateTypedEventsTask::outputDir)
        }
    }
}
