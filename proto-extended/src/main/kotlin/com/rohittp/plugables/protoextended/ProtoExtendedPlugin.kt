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

        // `.matching { }.configureEach { }` rather than a one-shot `findByName` lookup:
        // `plugins.withId("org.jetbrains.kotlin.multiplatform")` fires the instant KMP is
        // applied — during the consumer's `plugins { }` block — strictly before the script
        // body's `kotlin { androidTarget() }` runs and creates `androidMain`. A point-in-time
        // `findByName("androidMain")` would always see it as absent. A live view configures
        // the source set whenever it is created, regardless of order. Applied uniformly to
        // all three lookups so none of them is order-dependent.
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlinExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlinExtension.sourceSets.matching { it.name == "commonMain" }.configureEach {
                this.kotlin.srcDir(metadataTask)
                metadataWired = true
            }
            kotlinExtension.sourceSets.matching { it.name == "androidMain" }.configureEach {
                this.kotlin.srcDir(resourcesTask)
                resourcesWired = true
            }
        }

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            val kotlinExtension = project.extensions.getByType(KotlinJvmProjectExtension::class.java)
            kotlinExtension.sourceSets.matching { it.name == "main" }.configureEach {
                this.kotlin.srcDir(metadataTask)
                metadataWired = true
            }
        }

        for (androidPluginId in listOf("com.android.application", "com.android.library")) {
            project.plugins.withId(androidPluginId) {
                val components = project.extensions.getByType(AndroidComponentsExtension::class.java)
                components.onVariants { variant ->
                    // `onVariants` runs after script evaluation, so plugin-presence checks here
                    // are reliable regardless of the order plugins were declared in — unlike an
                    // eager check in `apply()`, which could run before a Kotlin plugin applied
                    // later in the same `plugins { }` block.
                    //
                    // Skip entirely under Kotlin Multiplatform: on a classic KMP +
                    // `com.android.library` module, the `androidTarget()` source-set branch
                    // above already wires both tasks via `androidMain`. Without this guard this
                    // branch would fire too and wire `generateProtoMetadata` twice, risking a
                    // `Redeclaration` error — `variant.sources.kotlin` is a non-nullable field in
                    // AGP (confirmed by decompiling `SourcesImpl` in 8.6.1 and 9.2.0); it is
                    // declared nullable in the public interface only for source compatibility and
                    // is never actually null, so it cannot be relied on to signal a KMP module.
                    if (project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) return@onVariants

                    // Only claim wiring where the generated Kotlin will genuinely be compiled: an
                    // Android module with no Kotlin plugin has `variant.sources.kotlin` populated
                    // regardless (see above), so without this check the diagnostic below could
                    // never fire for that module even though nothing consumes the source dir.
                    if (!project.plugins.hasPlugin("org.jetbrains.kotlin.android")) return@onVariants

                    val sources = variant.sources.kotlin ?: return@onVariants
                    sources.addGeneratedSourceDirectory(metadataTask, GenerateProtoMetadataTask::outputDir)
                    sources.addGeneratedSourceDirectory(resourcesTask, GenerateProtoAndroidResourcesTask::outputDir)
                    metadataWired = true
                    resourcesWired = true
                }
            }
        }

        // The one permitted afterEvaluate: purely diagnostic, nothing in the task graph
        // depends on it. Without this, a consumer with no recognised plugin gets a
        // successful generate task whose output nothing ever compiles.
        project.afterEvaluate {
            warnIfUnwired(project, "metadata", extension.metadata, metadataWired, metadataTask)
            warnIfUnwired(project, "androidResources", extension.androidResources, resourcesWired, resourcesTask)
            warnIfProtoDirMissing(project, "metadata", extension.metadata)
            warnIfProtoDirMissing(project, "androidResources", extension.androidResources)
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
            "w: protoExtended { $blockName { … } } is configured, but nothing wired it into a " +
                "source set. Generated sources in " +
                "${spec.outputDir.get().asFile.relativeTo(project.projectDir)} are not on any " +
                "source set. Add them manually with " +
                "kotlin.srcDir(tasks.named(\"${task.name}\")) if that is intentional.",
        )
    }

    /**
     * Independent of [warnIfUnwired]: a mistyped [ProtoSpec.protoDir] resolves to an empty
     * file tree, which `@SkipWhenEmpty` treats as `NO-SOURCE` — a successful build having
     * generated nothing, with wiring reported as fine because it genuinely is. This is the
     * other half of the "never fail silently" premise: it fires even when wiring succeeded.
     */
    private fun warnIfProtoDirMissing(project: Project, blockName: String, spec: ProtoSpec) {
        if (spec.protoDir.isPresent && !spec.protoDir.get().asFile.isDirectory) {
            project.logger.warn(
                "w: protoExtended { $blockName { protoDir … } } points at " +
                    "${spec.protoDir.get().asFile}, which does not exist. Nothing will be generated.",
            )
        }
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
