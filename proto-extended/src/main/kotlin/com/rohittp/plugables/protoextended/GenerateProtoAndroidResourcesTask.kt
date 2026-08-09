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
 * Generates `@get:StringRes val X.displayName` / `@get:DrawableRes val X.icon`
 * for enums carrying the `(gen.resources)` option.
 *
 * Must run in the module that owns the resources — a KMP library cannot see the
 * consuming app's `R`.
 *
 * `@CacheableTask` follows this repo's convention for deterministic generator tasks;
 * Gradle's `validatePlugins` task also requires every task type to declare a caching stance.
 */
@CacheableTask
abstract class GenerateProtoAndroidResourcesTask : DefaultTask() {

    @get:Internal
    abstract val protoDir: DirectoryProperty

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val protoFiles: ConfigurableFileCollection

    @get:Input
    abstract val basePackage: Property<String>

    @get:Input
    abstract val rPackage: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val enums = ProtoSchemaReader(protoDir.get().asFile).read()
        val source = AndroidResourceRenderer.render(basePackage.get(), rPackage.get(), enums)
        writeGenerated(outputDir.get().asFile, basePackage.get(), "ProtoEnumResources.kt", source)
    }
}
