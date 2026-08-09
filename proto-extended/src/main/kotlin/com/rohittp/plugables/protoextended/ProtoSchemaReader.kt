package com.rohittp.plugables.protoextended

import com.squareup.wire.schema.EnumConstant
import com.squareup.wire.schema.EnumType
import com.squareup.wire.schema.Field
import com.squareup.wire.schema.Location
import com.squareup.wire.schema.MessageType
import com.squareup.wire.schema.Options
import com.squareup.wire.schema.ProtoFile
import com.squareup.wire.schema.ProtoMember
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
        val metaSpecs = discoverMetaSpecs(schema)

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
                    metaProperties = metaProperties(enumType, metaSpecs),
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

    /** One `extend google.protobuf.EnumValueOptions` field and the message it points at. */
    private data class MetaOptionSpec(
        val optionName: String,
        val optionMember: ProtoMember,
        val metaType: ProtoType,
        val metaMessage: MessageType,
    )

    private fun discoverMetaSpecs(schema: Schema): List<MetaOptionSpec> =
        schema.protoFiles
            .flatMap { it.extendList }
            .filter { it.type == Options.ENUM_VALUE_OPTIONS }
            .flatMap { it.fields }
            .mapNotNull { field ->
                val fieldType = field.type ?: return@mapNotNull null
                val message = schema.getType(fieldType) as? MessageType ?: return@mapNotNull null
                MetaOptionSpec(
                    optionName = field.name,
                    optionMember = ProtoMember.get(Options.ENUM_VALUE_OPTIONS, field.qualifiedName),
                    metaType = fieldType,
                    metaMessage = message,
                )
            }

    @Suppress("UNCHECKED_CAST")
    private fun metaProperties(
        enumType: EnumType,
        specs: List<MetaOptionSpec>,
    ): List<MetaProperty> {
        val properties = mutableListOf<MetaProperty>()
        val seenNames = mutableMapOf<String, String>()

        for (spec in specs) {
            val optionsByConstant: Map<EnumConstant, Map<ProtoMember, Any?>?> =
                enumType.constants.associateWith { constant ->
                    constant.options.get(spec.optionMember) as? Map<ProtoMember, Any?>
                }

            val carrying = optionsByConstant.filterValues { it != null }
            if (carrying.isEmpty()) continue

            // Rule 1 — all-or-nothing option presence.
            val missing = optionsByConstant.filterValues { it == null }.keys.map { it.name }
            if (missing.isNotEmpty()) {
                throw ProtoSchemaException(
                    "Enum `${enumType.type}` declares (${spec.optionName}) on " +
                        "${carrying.size} of ${enumType.constants.size} constants. Missing on:\n" +
                        missing.joinToString("\n") { "  - $it" } +
                        "\n\nEvery constant must set the option, or none.",
                )
            }

            for (field in spec.metaMessage.declaredFields) {
                // Rule 4 — supported scalars only.
                val scalar = scalarFor(field)
                    ?: throw ProtoSchemaException(
                        "Field `${field.name}` of `${spec.metaType}` has unsupported type " +
                            "`${field.type}`${if (field.isRepeated) " (repeated)" else ""}. " +
                            "proto-extended supports string, double, float, int32, uint32, " +
                            "int64, uint64 and bool.",
                    )

                val member = ProtoMember.get(spec.metaType, field.name)
                val rawByConstant = optionsByConstant.mapValues { (_, options) ->
                    options?.get(member)?.toString()
                }

                val setCount = rawByConstant.count { it.value != null }
                if (setCount == 0) continue

                // Rule 2 — all-or-nothing field presence.
                if (setCount != enumType.constants.size) {
                    val unset = rawByConstant.filterValues { it == null }.keys.map { it.name }
                    throw ProtoSchemaException(
                        "Field `${field.name}` of (${spec.optionName}) is set on $setCount of " +
                            "${enumType.constants.size} constants of `${enumType.type}`. Missing on:\n" +
                            unset.joinToString("\n") { "  - $it" } +
                            "\n\nSet it on every constant, or none.",
                    )
                }

                // Rule 3 — reserved names and cross-message collisions.
                if (field.name in RESERVED_NAMES) {
                    throw ProtoSchemaException(
                        "Field `${field.name}` of `${spec.metaType}` is reserved: an extension " +
                            "property cannot shadow the enum member of the same name, so the " +
                            "generated property would silently never be called. " +
                            "Reserved names: ${RESERVED_NAMES.joinToString()}.",
                    )
                }
                seenNames[field.name]?.let { previous ->
                    throw ProtoSchemaException(
                        "Field `${field.name}` is contributed to enum `${enumType.type}` by both " +
                            "`$previous` and `${spec.metaType}`. Rename one of them.",
                    )
                }
                seenNames[field.name] = spec.metaType.toString()

                properties += MetaProperty(
                    name = field.name,
                    type = scalar,
                    values = enumType.constants.map { constant ->
                        ConstantValue(constant.name, rawByConstant.getValue(constant)!!)
                    },
                )
            }
        }
        return properties
    }

    private fun scalarFor(field: Field): KotlinScalar? {
        if (field.isRepeated) return null
        return when (field.type) {
            ProtoType.STRING -> KotlinScalar.STRING
            ProtoType.DOUBLE -> KotlinScalar.DOUBLE
            ProtoType.FLOAT -> KotlinScalar.FLOAT
            ProtoType.INT32, ProtoType.UINT32 -> KotlinScalar.INT
            ProtoType.INT64, ProtoType.UINT64 -> KotlinScalar.LONG
            ProtoType.BOOL -> KotlinScalar.BOOLEAN
            else -> null
        }
    }

    private companion object {
        /**
         * Names an extension property must not take. `name` and `ordinal` are Kotlin
         * `Enum` members; `value` is Wire's `WireEnum.value`. A real member always wins
         * over an extension, so a clash is dead code with no compiler error.
         */
        val RESERVED_NAMES = setOf("name", "ordinal", "value")
    }
}
