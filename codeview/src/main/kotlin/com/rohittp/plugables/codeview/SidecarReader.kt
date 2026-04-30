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

    /**
     * Iterative JSON parser tuned to codeview's sidecar shape. Replaces the previous regex-based
     * approach which stack-overflowed on long string payloads (e.g. multi-KB stack traces in
     * the `renderError` field) due to Java's regex `(?:[^"\\]|\\.)*` matcher recursion.
     */
    fun read(json: String): Parsed {
        val root = JsonParser(json).parseValue() as? Map<*, *>
            ?: return Parsed(0, 0, emptyList(), emptyList(), 1, null, null)

        val schema = (root["schemaVersion"] as? Long)?.toInt() ?: 1
        val sourceHash = root["sourceHash"] as? String
        val renderError = root["renderError"] as? String
        val w = (root["imageWidth"] as? Long)?.toInt() ?: 0
        val h = (root["imageHeight"] as? Long)?.toInt() ?: 0

        @Suppress("UNCHECKED_CAST")
        val nodes = (root["nodes"] as? List<*>)?.mapNotNull { n ->
            val node = n as? Map<*, *> ?: return@mapNotNull null
            val bounds = node["bounds"] as? Map<*, *> ?: return@mapNotNull null
            RawNode(
                id = (node["id"] as? Long)?.toInt() ?: 0,
                name = node["name"] as? String ?: "",
                bounds = Bounds(
                    x = (bounds["x"] as? Long)?.toInt() ?: 0,
                    y = (bounds["y"] as? Long)?.toInt() ?: 0,
                    width = (bounds["width"] as? Long)?.toInt() ?: 0,
                    height = (bounds["height"] as? Long)?.toInt() ?: 0,
                ),
                sourceFileName = node["sourceFileName"] as? String,
                packageHash = (node["packageHash"] as? Long)?.toInt() ?: 0,
                line = (node["line"] as? Long)?.toInt() ?: -1,
                parentId = (node["parentId"] as? Long)?.toInt(),
            )
        } ?: emptyList()

        val texts = (root["renderedTexts"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        return Parsed(w, h, nodes, texts, schema, sourceHash, renderError)
    }

    /**
     * Minimal JSON parser. Iterates over the input character by character; the only recursion
     * is structural (object/array nesting), which is bounded to ~3–4 levels for codeview's
     * sidecar shape — never proportional to string content length.
     */
    private class JsonParser(private val s: String) {
        private var i = 0

        fun parseValue(): Any? {
            skipWs()
            if (i >= s.length) return null
            return when (val c = s[i]) {
                '"' -> parseString()
                '{' -> parseObject()
                '[' -> parseArray()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> error("Unexpected char '$c' at index $i")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            skipWs()
            val map = LinkedHashMap<String, Any?>()
            if (peekOrNull() == '}') { i++; return map }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                map[key] = parseValue()
                skipWs()
                when (peekOrNull()) {
                    ',' -> { i++; continue }
                    '}' -> { i++; return map }
                    else -> error("Expected ',' or '}' at $i")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            skipWs()
            val list = mutableListOf<Any?>()
            if (peekOrNull() == ']') { i++; return list }
            while (true) {
                list.add(parseValue())
                skipWs()
                when (peekOrNull()) {
                    ',' -> { i++; continue }
                    ']' -> { i++; return list }
                    else -> error("Expected ',' or ']' at $i")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (i < s.length) {
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) error("Unterminated escape at $i")
                        when (val esc = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('')
                            'u' -> {
                                if (i + 4 > s.length) error("Truncated unicode escape at $i")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> sb.append(esc)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            error("Unterminated string at $i")
        }

        private fun parseNumber(): Any {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            var isFloat = false
            if (i < s.length && s[i] == '.') {
                isFloat = true; i++
                while (i < s.length && s[i].isDigit()) i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                isFloat = true; i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i].isDigit()) i++
            }
            val text = s.substring(start, i)
            return if (isFloat) text.toDouble() else text.toLong()
        }

        private fun parseBoolean(): Boolean = when {
            s.startsWith("true", i) -> { i += 4; true }
            s.startsWith("false", i) -> { i += 5; false }
            else -> error("Expected boolean at $i")
        }

        private fun parseNull(): Any? {
            if (!s.startsWith("null", i)) error("Expected null at $i")
            i += 4
            return null
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun peekOrNull(): Char? = if (i < s.length) s[i] else null

        private fun expect(c: Char) {
            if (i >= s.length || s[i] != c) error("Expected '$c' at $i, got '${peekOrNull() ?: "EOF"}'")
            i++
        }
    }
}
