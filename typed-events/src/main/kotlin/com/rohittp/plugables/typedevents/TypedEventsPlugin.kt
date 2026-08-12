package com.rohittp.plugables.typedevents

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
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

        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            wireKotlinSourceSet(project, "commonMain", generateTask)
        }

        // Wire Android generated sources using the Variant API (AGP 7.2+)
        project.plugins.withId("com.android.application") {
            if (!project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                AndroidSourceWiring.wire(project, generateTask)
            }
        }
        project.plugins.withId("com.android.library") {
            if (!project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                AndroidSourceWiring.wire(project, generateTask)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun wireKotlinSourceSet(
        project: Project,
        sourceSetName: String,
        task: TaskProvider<GenerateTypedEventsTask>,
    ) {
        val kotlinExtension = project.extensions.getByName("kotlin")
        val sourceSetsGetter = kotlinExtension.javaClass.methods.singleOrNull {
            it.name == "getSourceSets" && it.parameterCount == 0
        } ?: error("Kotlin extension does not expose sourceSets")
        val sourceSets = sourceSetsGetter.invoke(kotlinExtension) as NamedDomainObjectContainer<Any>
        sourceSets.matching { (it as Named).name == sourceSetName }.configureEach(Action<Any> {
            val sourceSet = this
            val kotlinGetter = sourceSet.javaClass.methods.singleOrNull {
                it.name == "getKotlin" && it.parameterCount == 0
            } ?: error("Kotlin source set `$sourceSetName` does not expose Kotlin sources")
            (kotlinGetter.invoke(sourceSet) as SourceDirectorySet).srcDir(task)
        })
    }
}
