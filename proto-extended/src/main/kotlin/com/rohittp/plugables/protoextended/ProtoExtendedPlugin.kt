package com.rohittp.plugables.protoextended

import org.gradle.api.Plugin
import org.gradle.api.Project

class ProtoExtendedPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("protoExtended", ProtoExtendedExtension::class.java)

        project.tasks.register(
            "generateProtoMetadata",
            GenerateProtoMetadataTask::class.java,
        ) {
            description = "Generates Kotlin extension properties from proto enum metadata options."
            protoDir.set(extension.metadata.protoDir)
            protoFiles.from(protoFilesOf(project, extension.metadata))
            basePackage.set(extension.metadata.basePackage)
            outputDir.set(
                extension.metadata.outputDir.convention(
                    project.layout.buildDirectory.dir("generated/source/protoExtended/metadata"),
                ),
            )
        }

        project.tasks.register(
            "generateProtoAndroidResources",
            GenerateProtoAndroidResourcesTask::class.java,
        ) {
            description = "Generates Android string/drawable accessors from proto enum resource options."
            protoDir.set(extension.androidResources.protoDir)
            protoFiles.from(protoFilesOf(project, extension.androidResources))
            basePackage.set(extension.androidResources.basePackage)
            rPackage.set(extension.androidResources.rPackage)
            outputDir.set(
                extension.androidResources.outputDir.convention(
                    project.layout.buildDirectory.dir("generated/source/protoExtended/androidResources"),
                ),
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
