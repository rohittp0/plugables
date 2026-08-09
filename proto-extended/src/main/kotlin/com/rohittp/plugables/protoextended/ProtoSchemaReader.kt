package com.rohittp.plugables.protoextended

import com.squareup.wire.schema.EnumType
import com.squareup.wire.schema.Location
import com.squareup.wire.schema.ProtoFile
import com.squareup.wire.schema.ProtoType
import com.squareup.wire.schema.Schema
import com.squareup.wire.schema.SchemaLoader
import okio.FileSystem
import java.io.File

/**
 * Turns a directory of `.proto` files into the plugin's model.
 *
 * Everything Wire-specific stops here: [ProtoEnumInfo] and friends carry no
 * wire-schema types, so the renderers stay pure and independently testable.
 */
class ProtoSchemaReader(private val protoDir: File) {

    fun read(): List<ProtoEnumInfo> {
        val schema = loadSchema()

        return schema.protoFiles
            .filter { !it.packageName.orEmpty().startsWith("google.protobuf") }
            .flatMap { it.types }
            .flatMap { it.typesAndNestedTypes() }
            .filterIsInstance<EnumType>()
            .map { enumType ->
                ProtoEnumInfo(
                    qualifiedName = enumType.type.toString(),
                    kotlinRef = relativeName(schema, enumType.type),
                    kotlinImport = kotlinImport(schema, enumType.type),
                    constantNames = enumType.constants.map { it.name },
                    metaProperties = emptyList(),
                    resourceFlags = ResourceFlags(),
                )
            }
            .sortedBy { it.qualifiedName }
    }

    private fun loadSchema(): Schema {
        val root = Location.get(protoDir.absolutePath)
        val loader = SchemaLoader(FileSystem.SYSTEM)
        loader.initRoots(sourcePath = listOf(root), protoPath = listOf(root))
        return loader.loadSchema()
    }

    /**
     * Mirrors Wire's own `KotlinGenerator` precedence (`JvmLanguages.javaPackage`):
     * `wire_package` beats `java_package` beats the proto package. Reimplemented rather
     * than called, because Wire's version lives in a `.internal.` package with no
     * compatibility promise.
     */
    private fun kotlinPackage(protoFile: ProtoFile?): String =
        protoFile?.wirePackage()
            ?: protoFile?.javaPackage()
            ?: protoFile?.packageName.orEmpty()

    /** `AspectRatio` for a top-level enum, `Distance.Unit` for a nested one. */
    private fun relativeName(schema: Schema, protoType: ProtoType): String {
        val pkg = schema.protoFile(protoType)?.packageName.orEmpty()
        val full = protoType.toString()
        return if (pkg.isNotEmpty() && full.startsWith("$pkg.")) full.removePrefix("$pkg.") else full
    }

    /**
     * The outermost class to import. For `Distance.Unit` that is `…Distance`, never
     * `…Unit` — importing the nested enum would shadow `kotlin.Unit` and would not
     * match the dotted reference emitted by the renderers.
     */
    private fun kotlinImport(schema: Schema, protoType: ProtoType): String {
        val pkg = kotlinPackage(schema.protoFile(protoType))
        val outer = relativeName(schema, protoType).substringBefore('.')
        return if (pkg.isNotEmpty()) "$pkg.$outer" else outer
    }
}
