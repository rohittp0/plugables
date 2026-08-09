package com.rohittp.plugables.protoextended

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class ProtoExtendedPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("protoExtended", ProtoExtendedExtension::class.java)

        // Conventions are set here, eagerly, rather than inside the lazy task-registration
        // blocks below: `warnIfUnwired` reads `spec.outputDir.get()` from `afterEvaluate`,
        // which runs before Gradle realizes a task it hasn't otherwise needed. A convention
        // attached only inside the (lazy) registration action would not yet be visible on
        // the extension's own property at that point, and `.get()` on a still-absent
        // property throws instead of letting the diagnostic report a path.
        extension.metadata.outputDir.convention(
            project.layout.buildDirectory.dir("generated/source/protoExtended/metadata"),
        )
        extension.androidResources.outputDir.convention(
            project.layout.buildDirectory.dir("generated/source/protoExtended/androidResources"),
        )

        val metadataTask = project.tasks.register(
            "generateProtoMetadata",
            GenerateProtoMetadataTask::class.java,
        ) {
            description = "Generates Kotlin extension properties from proto enum metadata options."
            protoDir.set(extension.metadata.protoDir)
            protoFiles.from(protoFilesOf(project, extension.metadata))
            basePackage.set(extension.metadata.basePackage)
            outputDir.set(extension.metadata.outputDir)
        }

        val resourcesTask = project.tasks.register(
            "generateProtoAndroidResources",
            GenerateProtoAndroidResourcesTask::class.java,
        ) {
            description = "Generates Android string/drawable accessors from proto enum resource options."
            protoDir.set(extension.androidResources.protoDir)
            protoFiles.from(protoFilesOf(project, extension.androidResources))
            basePackage.set(extension.androidResources.basePackage)
            rPackage.set(extension.androidResources.rPackage)
            outputDir.set(extension.androidResources.outputDir)
        }

        var metadataWired = false
        var resourcesWired = false

        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlin.sourceSets.findByName("commonMain")?.let {
                it.kotlin.srcDir(metadataTask)
                metadataWired = true
            }
            kotlin.sourceSets.findByName("androidMain")?.let {
                it.kotlin.srcDir(resourcesTask)
                resourcesWired = true
            }
        }

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            val kotlin = project.extensions.getByType(KotlinJvmProjectExtension::class.java)
            kotlin.sourceSets.findByName("main")?.let {
                it.kotlin.srcDir(metadataTask)
                metadataWired = true
            }
        }

        for (androidPluginId in listOf("com.android.application", "com.android.library")) {
            project.plugins.withId(androidPluginId) {
                val components = project.extensions.getByType(AndroidComponentsExtension::class.java)
                components.onVariants { variant ->
                    variant.sources.kotlin?.addGeneratedSourceDirectory(
                        metadataTask,
                        GenerateProtoMetadataTask::outputDir,
                    )
                    variant.sources.kotlin?.addGeneratedSourceDirectory(
                        resourcesTask,
                        GenerateProtoAndroidResourcesTask::outputDir,
                    )
                }
                metadataWired = true
                resourcesWired = true
            }
        }

        // The one permitted afterEvaluate: purely diagnostic, nothing in the task graph
        // depends on it. Without this, a consumer with no recognised plugin gets a
        // successful generate task whose output nothing ever compiles.
        project.afterEvaluate {
            warnIfUnwired(project, "metadata", extension.metadata, metadataWired, metadataTask)
            warnIfUnwired(project, "androidResources", extension.androidResources, resourcesWired, resourcesTask)
        }
    }

    private fun warnIfUnwired(
        project: Project,
        blockName: String,
        spec: ProtoSpec,
        wired: Boolean,
        task: TaskProvider<*>,
    ) {
        if (wired || !spec.protoDir.isPresent) return
        project.logger.warn(
            "w: protoExtended { $blockName { … } } is configured, but no Kotlin Multiplatform, " +
                "Kotlin JVM or Android plugin was found to wire it into. Generated sources in " +
                "${spec.outputDir.get().asFile.relativeTo(project.projectDir)} are not on any " +
                "source set. Add them manually with " +
                "kotlin.srcDir(tasks.named(\"${task.name}\")) if that is intentional.",
        )
    }

    /**
     * An absent [ProtoSpec.protoDir] would make `spec.protoDir.map { ... }` an absent
     * provider. That is enough for `@SkipWhenEmpty` to treat the input as empty when
     * Gradle only checks *presence* — but `ConfigurableFileCollection.from(Provider<FileTree>)`
     * also resolves the provider during task-dependency determination (to inspect the
     * resolved `FileTree`'s build dependencies), and calling `.get()` on a genuinely absent
     * provider throws there instead of skipping gracefully. `.orElse(...)` keeps the
     * provider always *present*, falling back to an empty, dependency-free `FileTree` —
     * still zero files, so `@SkipWhenEmpty` still reports `NO-SOURCE`, but nothing ever
     * queries an absent provider.
     */
    private fun protoFilesOf(project: Project, spec: ProtoSpec) =
        spec.protoDir
            .map { dir -> dir.asFileTree.matching { include("**/*.proto") } }
            .orElse(project.files().asFileTree)
}
