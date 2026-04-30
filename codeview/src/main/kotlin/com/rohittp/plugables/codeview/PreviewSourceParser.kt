package com.rohittp.plugables.codeview

import java.io.File

object PreviewSourceParser {

    fun parseFile(file: File): List<PreviewSpec> {
        val raw = file.readText()
        val packageName = packagePattern.find(raw)?.groupValues?.get(1) ?: return emptyList()

        val stripped = raw
            .replace(blockCommentPattern, "")
            .replace(lineCommentPattern, "")

        val results = mutableListOf<PreviewSpec>()
        for (match in previewFunPattern.findAll(stripped)) {
            val funName = match.groupValues[1]
            val annotationStart = match.range.first
            val line = stripped.substring(0, annotationStart).count { it == '\n' } + 1
            val previewFqn = "$packageName.$funName"
            val disambiguator = "${file.absolutePath}|$previewFqn|${results.size}"
                .hashCode().toUInt().toString(16).padStart(8, '0')
            results.add(
                PreviewSpec(
                    id = "Codeview_${funName}_$disambiguator",
                    previewFqn = previewFqn,
                    displayName = funName,
                    source = SourceLocation(file = file.absolutePath, line = line),
                )
            )
        }
        return results
    }

    private val packagePattern = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)
    private val blockCommentPattern = Regex("""/\*[\s\S]*?\*/""")
    private val lineCommentPattern = Regex("""//[^\n]*""")
    private val previewFunPattern = Regex("""@Preview\b(?:\s*\([^)]*\))?[\s\S]*?fun\s+(\w+)\s*\(""")
}
