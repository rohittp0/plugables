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

        project.afterEvaluate {
            val android = project.extensions.findByName("android") as? BaseExtension
            if (android == null) {
                project.logger.warn(
                    "[viewmodel-stub] Android plugin not found in project '${project.name}'. " +
                    "This plugin targets Android Kotlin projects only — source set wiring skipped."
                )
            } else {
                // Pass the Provider<Directory> directly — defers resolution and is configuration-cache safe
                android.sourceSets.getByName("main").java.srcDirs(ext.outputDir)
            }

            // Ensure generation runs before any Kotlin compilation task
            project.tasks.matching { t -> t.name.matches(Regex("compile.*Kotlin")) }
                .configureEach(Action { dependsOn(generateTask) })
        }
    }
}
