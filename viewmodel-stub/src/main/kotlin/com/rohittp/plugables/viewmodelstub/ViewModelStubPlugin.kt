package com.rohittp.plugables.viewmodelstub

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

class ViewModelStubPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create("viewModelStub", ViewModelStubExtension::class.java)

        val generateTask = project.tasks.register(
            "generateViewModelStubs",
            GenerateViewModelStubsTask::class.java,
            Action {
                sourceDir.set(ext.sourceDir)
                outputDir.set(ext.outputDir)
            }
        )

        // Wire before any Kotlin compile task — lazy, no afterEvaluate needed
        project.tasks.matching { t -> t.name.matches(Regex("compile.*Kotlin")) }
            .configureEach(Action { dependsOn(generateTask) })

        // Wire Android generated sources using the Variant API (AGP 7.2+)
        // plugins.withId fires immediately if the plugin is already applied
        project.plugins.withId("com.android.application") {
            wireAndroidSources(project, generateTask)
        }
        project.plugins.withId("com.android.library") {
            wireAndroidSources(project, generateTask)
        }
    }

    private fun wireAndroidSources(project: Project, generateTask: TaskProvider<GenerateViewModelStubsTask>) {
        val androidComponents = project.extensions.getByType(
            AndroidComponentsExtension::class.java
        )
        androidComponents.onVariants { variant ->
            variant.sources.java?.addGeneratedSourceDirectory(generateTask, GenerateViewModelStubsTask::outputDir)
            variant.sources.kotlin?.addGeneratedSourceDirectory(generateTask, GenerateViewModelStubsTask::outputDir)
        }
    }
}
