package com.rohittp.plugables.protoextended

/**
 * Renders the Android half: `@get:StringRes val X.displayName` and
 * `@get:DrawableRes val X.icon`, resolving `R.string.<constant>` and
 * `R.drawable.<constant>` with the constant name lowercased.
 *
 * This is the only generated output that cannot live in `commonMain`, because
 * `R` belongs to the module that owns the resources.
 */
object AndroidResourceRenderer {

    fun render(basePackage: String, rPackage: String, enums: List<ProtoEnumInfo>): String {
        val body = StringBuilder()
        val enumImports = sortedSetOf<String>()
        var usesStringRes = false
        var usesDrawableRes = false

        for (enum in enums.sortedBy { it.qualifiedName }) {
            if (!enum.resourceFlags.any) continue
            enumImports.add(enum.kotlinImport)

            if (enum.resourceFlags.displayName) {
                usesStringRes = true
                appendAccessor(body, enum, "displayName", "@get:StringRes", "R.string")
            }
            if (enum.resourceFlags.icon) {
                usesDrawableRes = true
                appendAccessor(body, enum, "icon", "@get:DrawableRes", "R.drawable")
            }
        }

        return buildString {
            appendLine("// GENERATED — do not edit. Source: proto enum definitions.")
            appendLine("package $basePackage")
            if (body.isNotEmpty()) {
                appendLine()
                if (usesDrawableRes) appendLine("import androidx.annotation.DrawableRes")
                if (usesStringRes) appendLine("import androidx.annotation.StringRes")
                appendLine("import $rPackage.R")
                for (import in enumImports) appendLine("import $import")
                appendLine()
                append(body)
            }
        }
    }

    private fun appendAccessor(
        body: StringBuilder,
        enum: ProtoEnumInfo,
        propertyName: String,
        annotation: String,
        resourcePrefix: String,
    ) {
        body.appendLine(annotation)
        body.appendLine("val ${enum.kotlinRef}.$propertyName: Int")
        body.appendLine("    get() = when (this) {")
        for (constant in enum.constantNames) {
            body.appendLine("        ${enum.kotlinRef}.$constant -> $resourcePrefix.${constant.lowercase()}")
        }
        body.appendLine("    }")
        body.appendLine()
    }
}
