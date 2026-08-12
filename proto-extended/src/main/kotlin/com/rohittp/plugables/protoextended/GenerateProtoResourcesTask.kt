package com.rohittp.plugables.protoextended

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

/**
 * Generates common `StringResource` and `DrawableResource` enum properties for enums carrying
 * `(gen.resources)`. The output package must match Compose's generated `Res` package.
 */
@CacheableTask
abstract class GenerateProtoResourcesTask : DefaultTask() {

    @get:Internal
    abstract val protoDir: DirectoryProperty

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val protoFiles: ConfigurableFileCollection

    @get:Input
    abstract val basePackage: Property<String>

    @get:Internal
    abstract val composeResourcesDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val composeResourceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val enums = ProtoSchemaReader(protoDir.get().asFile).read()
        ComposeResourceValidator.validate(composeResourcesDir.get().asFile, enums)
        val source = ComposeResourceRenderer.render(basePackage.get(), enums)
        writeGenerated(outputDir.get().asFile, basePackage.get(), "ProtoEnumResources.kt", source)
    }
}
