package com.rohittp.plugables.protoextended

import java.io.File

/**
 * Clears the output directory and writes [fileName] under the package path.
 *
 * The wipe matters because `basePackage` is an input: changing it would otherwise
 * leave the previous package's file behind as a stale, still-compiled source. Shared by
 * both [GenerateProtoMetadataTask] and [GenerateProtoAndroidResourcesTask].
 */
internal fun writeGenerated(outputDir: File, basePackage: String, fileName: String, source: String) {
    outputDir.deleteRecursively()
    val packageDir = File(outputDir, basePackage.replace('.', '/'))
    packageDir.mkdirs()
    File(packageDir, fileName).writeText(source)
}
