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
import java.io.File

/**
 * Generates pure-Kotlin extension properties from proto enum metadata options.
 *
 * [protoFiles] rather than [protoDir] carries the input tracking: a required
 * `@InputDirectory` would fail at snapshot time when the block is unconfigured,
 * defeating the `NO-SOURCE` skip that makes an unused block harmless.
 *
 * `@CacheableTask` follows this repo's convention for deterministic generator tasks
 * (see `GenerateAutoAssertAnnotationsTask`, `GenerateViewModelStubsTask`, etc.); Gradle's
 * `validatePlugins` task also requires every task type to declare a caching stance.
 */
@CacheableTask
abstract class GenerateProtoMetadataTask : DefaultTask() {

    @get:Internal
    abstract val protoDir: DirectoryProperty

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val protoFiles: ConfigurableFileCollection

    @get:Input
    abstract val basePackage: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val enums = ProtoSchemaReader(protoDir.get().asFile).read()
        val source = MetadataRenderer.render(basePackage.get(), enums)
        writeGenerated(outputDir.get().asFile, basePackage.get(), "ProtoEnumMetadata.kt", source)
    }
}

/**
 * Clears the output directory and writes [fileName] under the package path.
 *
 * The wipe matters because `basePackage` is an input: changing it would otherwise
 * leave the previous package's file behind as a stale, still-compiled source.
 */
internal fun writeGenerated(outputDir: File, basePackage: String, fileName: String, source: String) {
    outputDir.deleteRecursively()
    val packageDir = File(outputDir, basePackage.replace('.', '/'))
    packageDir.mkdirs()
    File(packageDir, fileName).writeText(source)
}
