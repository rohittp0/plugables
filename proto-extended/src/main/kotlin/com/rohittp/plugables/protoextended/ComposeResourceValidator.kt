package com.rohittp.plugables.protoextended

import org.gradle.api.GradleException
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Validates the base fallback resources referenced by generated enum accessors. */
object ComposeResourceValidator {

    fun validate(composeResourcesDir: File, enums: List<ProtoEnumInfo>) {
        val optedIn = enums.filter { it.resourceFlags.any }
        if (optedIn.isEmpty()) return

        val valuesFile = composeResourcesDir.resolve("values/strings.xml")
        val stringNames = if (valuesFile.isFile) readStringNames(valuesFile) else emptySet()
        val drawableDir = composeResourcesDir.resolve("drawable")
        val drawableNames = drawableDir.listFiles()
            .orEmpty()
            .filter(File::isFile)
            .mapTo(mutableSetOf()) { it.nameWithoutExtension }

        val missing = buildList {
            for (enum in optedIn.sortedBy { it.qualifiedName }) {
                for (constant in enum.constantNames) {
                    val resourceName = constant.lowercase()
                    if (enum.resourceFlags.displayName && resourceName !in stringNames) {
                        add(
                            "${enum.qualifiedName}.$constant expects string `$resourceName` in " +
                                "${valuesFile.path}",
                        )
                    }
                    if (enum.resourceFlags.icon && resourceName !in drawableNames) {
                        add(
                            "${enum.qualifiedName}.$constant expects drawable `$resourceName` in " +
                                "${drawableDir.path}",
                        )
                    }
                }
            }
        }

        if (missing.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("proto-extended Compose resource validation failed:")
                    missing.forEach { appendLine("- $it") }
                    append("Add or rename the base Compose resources before generating accessors.")
                },
            )
        }
    }

    private fun readStringNames(file: File): Set<String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildSet {
            for (index in 0 until nodes.length) {
                nodes.item(index).attributes?.getNamedItem("name")?.nodeValue?.let(::add)
            }
        }
    }
}
