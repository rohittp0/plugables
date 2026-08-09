package com.rohittp.plugables.protoextended

/**
 * Renders the pure-Kotlin half: one extension property per metadata field.
 *
 * Nothing here is Android-specific, so the output compiles for every KMP target.
 * Enums are sorted by qualified name and imports are sorted so that reordering
 * proto files does not churn the output and bust the build cache.
 */
object MetadataRenderer {

    fun render(basePackage: String, enums: List<ProtoEnumInfo>): String {
        val body = StringBuilder()
        val imports = sortedSetOf<String>()

        for (enum in enums.sortedBy { it.qualifiedName }) {
            if (enum.metaProperties.isEmpty()) continue
            imports.add(enum.kotlinImport)
            for (property in enum.metaProperties) {
                body.appendLine("val ${enum.kotlinRef}.${property.name}: ${property.type.kotlinName}")
                body.appendLine("    get() = when (this) {")
                for (value in property.values) {
                    body.appendLine("        ${enum.kotlinRef}.${value.constantName} -> ${literal(property.type, value.rawValue)}")
                }
                body.appendLine("    }")
                body.appendLine()
            }
        }

        return buildString {
            appendLine("// GENERATED — do not edit. Source: proto enum definitions.")
            appendLine("package $basePackage")
            if (imports.isNotEmpty()) {
                appendLine()
                for (import in imports) appendLine("import $import")
            }
            if (body.isNotEmpty()) {
                appendLine()
                append(body)
            }
        }
    }

    /**
     * Converts a raw proto option value into a Kotlin literal. Int tolerates a
     * decimal-looking source value (`"1080.0"`), which protoc permits for integer
     * option fields.
     */
    private fun literal(type: KotlinScalar, raw: String): String = when (type) {
        KotlinScalar.STRING -> "\"${raw.escapeKotlin()}\""
        KotlinScalar.DOUBLE -> (raw.toDoubleOrNull() ?: 0.0).toString()
        KotlinScalar.FLOAT -> "${raw.toFloatOrNull() ?: 0.0f}f"
        KotlinScalar.INT -> "${raw.toDoubleOrNull()?.toInt() ?: 0}"
        KotlinScalar.LONG -> "${raw.toLongOrNull() ?: 0L}L"
        KotlinScalar.BOOLEAN -> raw.toBooleanStrictOrNull()?.toString() ?: "false"
    }

    private fun String.escapeKotlin(): String = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("$", "\\$")
}
