package com.rohittp.plugables.codeview

import java.io.File
import java.security.MessageDigest

object PreviewSourceParser {

    data class ParseResult(
        val specs: List<PreviewSpec>,
        /** `previewFqn`s skipped because they are top-level `private fun` and unreachable from generated tests. */
        val skippedPrivate: List<String>,
    )

    fun parseFile(file: File): ParseResult {
        val rawBytes = file.readBytes()
        val raw = rawBytes.toString(Charsets.UTF_8)
        val packageName = packagePattern.find(raw)?.groupValues?.get(1) ?: return ParseResult(emptyList(), emptyList())

        val stripped = raw
            .replace(blockCommentPattern, "")
            .replace(lineCommentPattern, "")

        // Hash once per file; every preview in this file shares the same source hash. This is
        // intentionally per-file, not per-preview-body — when the file changes we re-render every
        // preview defined in it. Document limitation: cross-file dependencies aren't tracked.
        val sourceHash = sha256Hex(rawBytes)

        val specs = mutableListOf<PreviewSpec>()
        val skippedPrivate = mutableListOf<String>()
        for (match in previewFunPattern.findAll(stripped)) {
            val funName = match.groupValues[1]
            val annotationStart = match.range.first
            val previewFqn = "$packageName.$funName"

            // Top-level `private fun` in Kotlin is file-private and cannot be invoked from a
            // generated test file. Skip and report so the user knows to widen visibility.
            if (Regex("""\bprivate\s+(?:[\w@:]+\s+)*fun\s+$funName\s*\(""").containsMatchIn(match.value)) {
                skippedPrivate.add(previewFqn)
                continue
            }

            val line = stripped.substring(0, annotationStart).count { it == '\n' } + 1
            val disambiguator = "${file.absolutePath}|$previewFqn|${specs.size}"
                .hashCode().toUInt().toString(16).padStart(8, '0')
            specs.add(
                PreviewSpec(
                    id = "Codeview_${funName}_$disambiguator",
                    previewFqn = previewFqn,
                    displayName = funName,
                    source = SourceLocation(file = file.absolutePath, line = line),
                    sourceHash = sourceHash,
                )
            )
        }
        return ParseResult(specs, skippedPrivate)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append("%02x".format(b))
        return sb.toString()
    }

    private val packagePattern = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)
    private val blockCommentPattern = Regex("""/\*[\s\S]*?\*/""")
    private val lineCommentPattern = Regex("""//[^\n]*""")
    private val previewFunPattern = Regex("""@Preview\b(?:\s*\([^)]*\))?[\s\S]*?fun\s+(\w+)\s*\(""")
}
