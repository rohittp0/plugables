package com.rohittp.plugables.codeview

object SidecarReader {

    data class RawNode(
        val id: Int,
        val name: String,
        val bounds: Bounds,
        val sourceFileName: String?,
        val packageHash: Int,
        val line: Int,
        val parentId: Int?,
    )

    data class Parsed(
        val imageWidth: Int,
        val imageHeight: Int,
        val nodes: List<RawNode>,
        val renderedTexts: List<String>,
        val schemaVersion: Int,
        val sourceHash: String?,
        /** Set when the per-preview render threw inside the batched test. */
        val renderError: String? = null,
    )

    fun read(json: String): Parsed {
        val schema = Regex("\"schemaVersion\":(\\d+)").find(json)?.groupValues?.get(1)?.toInt() ?: 1
        val sourceHash = Regex("\"sourceHash\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)
        val renderError = Regex("\"renderError\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)?.unescape()
        val w = Regex("\"imageWidth\":(\\d+)").find(json)?.groupValues?.get(1)?.toInt() ?: 0
        val h = Regex("\"imageHeight\":(\\d+)").find(json)?.groupValues?.get(1)?.toInt() ?: 0
        val nodes = mutableListOf<RawNode>()
        val nodeRegex = Regex(
            """\{"id":(\d+),"name":"((?:[^"\\]|\\.)*)","bounds":\{"x":(-?\d+),"y":(-?\d+),"width":(-?\d+),"height":(-?\d+)\},"sourceFileName":(null|"((?:[^"\\]|\\.)*)"),"packageHash":(-?\d+),"line":(-?\d+),"parentId":(null|\d+)\}"""
        )
        for (m in nodeRegex.findAll(json)) {
            nodes.add(RawNode(
                id = m.groupValues[1].toInt(),
                name = m.groupValues[2].unescape(),
                bounds = Bounds(
                    x = m.groupValues[3].toInt(),
                    y = m.groupValues[4].toInt(),
                    width = m.groupValues[5].toInt(),
                    height = m.groupValues[6].toInt(),
                ),
                sourceFileName = if (m.groupValues[7] == "null") null else m.groupValues[8].unescape(),
                packageHash = m.groupValues[9].toInt(),
                line = m.groupValues[10].toInt(),
                parentId = if (m.groupValues[11] == "null") null else m.groupValues[11].toInt(),
            ))
        }
        return Parsed(w, h, nodes, parseRenderedTexts(json), schema, sourceHash, renderError)
    }

    private fun parseRenderedTexts(json: String): List<String> {
        // Pull "renderedTexts":[ ... ] block, then split into individual JSON-quoted strings.
        val block = Regex("\"renderedTexts\":\\[((?:[^\\[\\]]|\\\\.)*)\\]").find(json)?.groupValues?.get(1) ?: return emptyList()
        val out = mutableListOf<String>()
        for (m in Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(block)) {
            out.add(m.groupValues[1].unescape())
        }
        return out
    }

    private fun String.unescape(): String =
        replace("\\\\", "\\").replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
}
