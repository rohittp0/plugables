package com.rohittp.plugables.codeview

object HtmlReportRenderer {

    private const val PLACEHOLDER = "__CODEVIEW_DATA__"
    private const val TEMPLATE_RESOURCE = "/codeview-report-template.html"

    fun render(
        previews: List<RenderedPreview>,
        imageDimensions: Map<String, Pair<Int, Int>>,
        ideScheme: String,
    ): String {
        val template = loadTemplate()
        val json = buildJson(previews, imageDimensions, ideScheme)
        return template.replace(PLACEHOLDER, json)
    }

    private fun loadTemplate(): String =
        HtmlReportRenderer::class.java.getResourceAsStream(TEMPLATE_RESOURCE)
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Missing $TEMPLATE_RESOURCE on classpath")

    private fun buildJson(
        previews: List<RenderedPreview>,
        imageDimensions: Map<String, Pair<Int, Int>>,
        ideScheme: String,
    ): String {
        val items = previews.joinToString(",") { rp ->
            val (w, h) = imageDimensions[rp.spec.id] ?: (0 to 0)
            val openUrl = openUrl(ideScheme, rp.spec.source)
            buildString {
                append('{')
                append("\"id\":").append(jsonString(rp.spec.id)).append(',')
                append("\"displayName\":").append(jsonString(rp.spec.displayName)).append(',')
                append("\"previewFqn\":").append(jsonString(rp.spec.previewFqn)).append(',')
                append("\"sourceFile\":").append(jsonString(rp.spec.source.file ?: "")).append(',')
                append("\"sourceLine\":").append(rp.spec.source.line).append(',')
                append("\"imagePath\":").append(if (rp.imagePath == null) "null" else jsonString(rp.imagePath)).append(',')
                append("\"imageWidth\":").append(w).append(',')
                append("\"imageHeight\":").append(h).append(',')
                append("\"openUrl\":").append(jsonString(openUrl)).append(',')
                append("\"renderedTexts\":[")
                append(rp.renderedTexts.joinToString(",") { jsonString(it) })
                append("],")
                append("\"nodes\":[")
                append(rp.nodes.joinToString(",") { n -> nodeJson(n, ideScheme) })
                append("]}")
            }
        }
        return "{\"schemaVersion\":1,\"previews\":[$items]}"
    }

    private fun nodeJson(n: NodeInfo, scheme: String): String = buildString {
        append('{')
        append("\"id\":").append(n.id).append(',')
        append("\"name\":").append(jsonString(n.name)).append(',')
        append("\"bounds\":{")
        append("\"x\":").append(n.bounds.x).append(',')
        append("\"y\":").append(n.bounds.y).append(',')
        append("\"width\":").append(n.bounds.width).append(',')
        append("\"height\":").append(n.bounds.height)
        append("},")
        append("\"sourceFile\":").append(jsonString(n.source.file ?: "")).append(',')
        append("\"sourceLine\":").append(n.source.line).append(',')
        append("\"openUrl\":").append(jsonString(openUrl(scheme, n.source))).append(',')
        append("\"codeSnippet\":").append(if (n.codeSnippet == null) "null" else jsonString(n.codeSnippet)).append(',')
        append("\"parentId\":").append(n.parentId?.toString() ?: "null")
        append('}')
    }

    private fun openUrl(scheme: String, src: SourceLocation): String =
        if (src.file == null) "" else IdeUrlBuilder.build(scheme, src.file, src.line)

    private fun jsonString(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (c in value) when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '<' -> sb.append("\\u003C")
            '>' -> sb.append("\\u003E")
            '&' -> sb.append("\\u0026")
            else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
        sb.append('"')
        return sb.toString()
    }
}
