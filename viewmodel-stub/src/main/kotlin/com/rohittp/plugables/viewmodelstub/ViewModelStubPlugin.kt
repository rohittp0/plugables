package com.rohittp.plugables.viewmodelstub

import com.android.build.gradle.BaseExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project

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

        // Wire Android source set when the Android plugin is applied
        // Using plugins.withId is configuration-cache safe (unlike afterEvaluate)
        val configureAndroid: (BaseExtension) -> Unit = { android ->
            android.sourceSets.getByName("main").java.srcDirs(ext.outputDir)
        }
        project.plugins.withId("com.android.application") {
            configureAndroid(project.extensions.getByType(BaseExtension::class.java))
        }
        project.plugins.withId("com.android.library") {
            configureAndroid(project.extensions.getByType(BaseExtension::class.java))
        }
    }
}
