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
     * decimal-looking *whole-number* source value (`"1080.0"`), which protoc permits for
     * integer option fields — but only when it is exactly representable as an `Int`:
     * a fractional value (`"1.5"`) or one outside `Int` range (`"4294967295"`, a legal
     * `uint32`) throws rather than being silently truncated or saturated.
     *
     * Never defaults a value that fails to parse — a defaulted `0` would be
     * indistinguishable from a genuinely-set `0`. Throws instead.
     */
    private fun literal(type: KotlinScalar, raw: String): String =
        when (type) {
            KotlinScalar.STRING -> "\"${raw.escapeKotlin()}\""
            KotlinScalar.DOUBLE -> raw.toDoubleOrNull()?.toString()
            KotlinScalar.FLOAT -> raw.toFloatOrNull()?.let { "${it}f" }
            KotlinScalar.INT -> raw.toExactIntOrNull()?.toString()
            KotlinScalar.LONG -> raw.toLongOrNull()?.let { "${it}L" }
            KotlinScalar.BOOLEAN -> raw.toBooleanStrictOrNull()?.toString()
        } ?: throw ProtoSchemaException(
            "Value `$raw` could not be represented as ${type.kotlinName}: it is out of " +
                "range, or not a valid ${type.kotlinName} literal.",
        )

    /**
     * An exact `Int` value, whether [this] is an integer literal or a decimal-looking
     * whole number in `Int` range (`"1080.0"`). Never truncates a fraction and never
     * saturates an out-of-range value — both fall through to `null`, which [literal]
     * turns into a thrown [ProtoSchemaException] rather than a silently wrong value.
     */
    private fun String.toExactIntOrNull(): Int? =
        toIntOrNull() ?: toDoubleOrNull()
            ?.takeIf { it == kotlin.math.floor(it) && it in INT_RANGE_AS_DOUBLE }
            ?.toInt()

    private fun String.escapeKotlin(): String = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("$", "\\$")

    private val INT_RANGE_AS_DOUBLE = Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()
}
