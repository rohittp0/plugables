package com.rohittp.plugables.typedevents

import com.android.build.gradle.BaseExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project

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

        project.afterEvaluate {
            val android = project.extensions.findByName("android") as? BaseExtension
            if (android == null) {
                project.logger.warn(
                    "[typed-events] Android plugin not found in project '${project.name}'. " +
                    "This plugin targets Android Kotlin projects only — source set wiring skipped."
                )
            } else {
                android.sourceSets.getByName("main").java.srcDirs(ext.outputDir)
            }

            project.tasks.matching { t -> t.name.matches(Regex("compile.*Kotlin")) }
                .configureEach(Action { dependsOn(generateTask) })
        }
    }
}
