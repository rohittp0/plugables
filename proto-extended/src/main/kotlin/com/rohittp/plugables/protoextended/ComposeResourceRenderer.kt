package com.rohittp.plugables.protoextended

/**
 * Renders common Compose Multiplatform resource accessors.
 *
 * [basePackage] must be the package configured for Compose's generated `Res` class. Keeping this
 * file in that package also places the generated `Res.string.*` and `Res.drawable.*` extension
 * properties in scope without wildcard imports or Android-specific resource identifiers.
 */
object ComposeResourceRenderer {

    fun render(basePackage: String, enums: List<ProtoEnumInfo>): String {
        val body = StringBuilder()
        val enumImports = sortedSetOf<String>()
        var usesStringResource = false
        var usesDrawableResource = false

        for (enum in enums.sortedBy { it.qualifiedName }) {
            if (!enum.resourceFlags.any) continue
            enumImports.add(enum.kotlinImport)

            if (enum.resourceFlags.displayName) {
                usesStringResource = true
                appendAccessor(body, enum, "displayName", "StringResource", "Res.string")
            }
            if (enum.resourceFlags.icon) {
                usesDrawableResource = true
                appendAccessor(body, enum, "icon", "DrawableResource", "Res.drawable")
            }
        }

        return buildString {
            appendLine("// GENERATED — do not edit. Source: proto enum definitions.")
            appendLine("package $basePackage")
            if (body.isNotEmpty()) {
                appendLine()
                if (usesDrawableResource) {
                    appendLine("import org.jetbrains.compose.resources.DrawableResource")
                }
                if (usesStringResource) {
                    appendLine("import org.jetbrains.compose.resources.StringResource")
                }
                for (enumImport in enumImports) appendLine("import $enumImport")
                appendLine()
                append(body)
            }
        }
    }

    private fun appendAccessor(
        body: StringBuilder,
        enum: ProtoEnumInfo,
        propertyName: String,
        resourceType: String,
        resourcePrefix: String,
    ) {
        body.appendLine("val ${enum.kotlinRef}.$propertyName: $resourceType")
        body.appendLine("    get() = when (this) {")
        for (constant in enum.constantNames) {
            body.appendLine("        ${enum.kotlinRef}.$constant -> $resourcePrefix.${constant.lowercase()}")
        }
        body.appendLine("    }")
        body.appendLine()
    }
}
