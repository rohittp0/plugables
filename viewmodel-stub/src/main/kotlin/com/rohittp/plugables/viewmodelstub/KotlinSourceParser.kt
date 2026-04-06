package com.rohittp.plugables.viewmodelstub

import java.io.File

/**
 * Regex-based Kotlin source parser that extracts public API surface from
 * classes annotated with `@ViewModelStub`.
 *
 * Limitations (by design):
 *  - Properties without an explicit type annotation are skipped.
 *  - Only class-level (depth-1) members are extracted.
 *  - Companion objects, init blocks, and nested classes are ignored.
 */
object KotlinSourceParser {

    fun parseFile(file: File): ViewModelInfo? {
        val content = file.readText()
        if (!content.contains("@ViewModelStub")) return null

        // Strip comments so we don't match @ViewModelStub in KDoc or line comments
        val strippedContent = content
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//.*$""", RegexOption.MULTILINE), "")
        if (!strippedContent.contains("@ViewModelStub")) return null

        val packageName = extractPackageName(content) ?: return null
        val imports = extractImports(content)
        val className = extractAnnotatedClassName(strippedContent) ?: return null

        if (!className.endsWith("Impl")) {
            error("@ViewModelStub class '$className' must have a name ending with 'Impl'")
        }

        val interfaceName = className.removeSuffix("Impl")
        val stubClassName = className.replace("Impl", "Stub")

        val classBody = extractClassBody(content, className) ?: return null
        val properties = extractProperties(classBody)
        val methods = extractMethods(classBody)

        return ViewModelInfo(
            packageName = packageName,
            implClassName = className,
            interfaceName = interfaceName,
            stubClassName = stubClassName,
            imports = imports,
            properties = properties,
            methods = methods,
        )
    }

    private fun extractPackageName(content: String): String? =
        Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE).find(content)?.groupValues?.get(1)

    private fun extractImports(content: String): List<String> =
        Regex("""^import\s+.+$""", RegexOption.MULTILINE).findAll(content).map { it.value }.toList()

    private fun extractAnnotatedClassName(content: String): String? {
        val pattern = Regex("""@ViewModelStub\b[\s\S]*?class\s+(\w+)""")
        return pattern.find(content)?.groupValues?.get(1)
    }

    private fun extractClassBody(content: String, className: String): String? {
        val classPattern = Regex("""class\s+$className\b""")
        val classMatch = classPattern.find(content) ?: return null
        val searchStart = classMatch.range.last + 1

        val openBrace = content.indexOf('{', searchStart)
        if (openBrace == -1) return null

        var depth = 1
        var i = openBrace + 1
        while (i < content.length && depth > 0) {
            when (content[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        return content.substring(openBrace + 1, i - 1)
    }

    private fun extractProperties(classBody: String): List<PropertyInfo> {
        val result = mutableListOf<PropertyInfo>()
        val lines = classBody.lines()
        var depth = 0

        for (line in lines) {
            val trimmed = line.trim()

            val depthBefore = depth
            depth += line.count { it == '{' } - line.count { it == '}' }

            if (depthBefore != 0) continue
            if (isNonPublic(trimmed)) continue
            if (trimmed.startsWith("init ") || trimmed.startsWith("init{") ||
                trimmed.startsWith("companion ") || trimmed.startsWith("class ") ||
                trimmed.startsWith("inner ") || trimmed.startsWith("data ") ||
                trimmed.startsWith("object ")
            ) continue

            val cleanedLine = trimmed
                .removePrefix("override ")
                .removePrefix("open ")
                .replace(Regex("""\s+get\s*\(.*"""), "")
                .replace(Regex("""\s+set\s*\(.*"""), "")

            val match = Regex("""^(val|var)\s+(\w+)\s*:\s*(.+?)(?:\s*=.*)?$""")
                .find(cleanedLine)

            if (match != null) {
                val isMutable = match.groupValues[1] == "var"
                val name = match.groupValues[2]
                val type = match.groupValues[3].trim()
                result.add(PropertyInfo(name, isMutable, type))
            }
        }
        return result
    }

    private fun extractMethods(classBody: String): List<MethodInfo> {
        val result = mutableListOf<MethodInfo>()
        val lines = classBody.lines()
        var depth = 0
        var collectingMethod = false
        var methodBuffer = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()

            if (!collectingMethod) {
                val depthBefore = depth
                depth += line.count { it == '{' } - line.count { it == '}' }

                if (depthBefore != 0) continue
                if (isNonPublic(trimmed)) continue

                val isFunLine = trimmed.matches(Regex("""^(?:override\s+)?(?:suspend\s+)?fun\s+.*"""))
                if (!isFunLine) continue

                methodBuffer = StringBuilder(trimmed)

                if (isSignatureComplete(methodBuffer.toString())) {
                    parseMethodSignature(methodBuffer.toString())?.let { result.add(it) }
                    collectingMethod = false
                } else {
                    collectingMethod = true
                }
            } else {
                methodBuffer.append(" ").append(trimmed)

                if (isSignatureComplete(methodBuffer.toString())) {
                    depth += line.count { it == '{' } - line.count { it == '}' }
                    parseMethodSignature(methodBuffer.toString())?.let { result.add(it) }
                    collectingMethod = false
                } else {
                    depth += line.count { it == '{' } - line.count { it == '}' }
                }
            }
        }
        return result
    }

    private fun isSignatureComplete(sig: String): Boolean {
        var parenDepth = 0
        for (c in sig) {
            when (c) {
                '(' -> parenDepth++
                ')' -> parenDepth--
            }
        }
        if (parenDepth != 0) return false
        val afterParens = sig.substringAfterLast(')').trim()
        return afterParens.contains('{') || afterParens.contains('=') ||
                afterParens.isEmpty() || afterParens.matches(Regex("""^:\s*\S+.*$"""))
    }

    private fun parseMethodSignature(raw: String): MethodInfo? {
        val cleaned = raw
            .removePrefix("override ")
            .removePrefix("open ")

        val isSuspend = cleaned.trimStart().startsWith("suspend ")
        val withoutSuspend = cleaned.removePrefix("suspend ").trimStart()

        val nameMatch = Regex("""^fun\s+(\w+)\s*\(""").find(withoutSuspend) ?: return null
        val name = nameMatch.groupValues[1]

        val paramsStart = withoutSuspend.indexOf('(')
        val paramsEnd = findMatchingParen(withoutSuspend, paramsStart)
        if (paramsEnd == -1) return null
        val paramsString = withoutSuspend.substring(paramsStart + 1, paramsEnd).trim()

        val afterParams = withoutSuspend.substring(paramsEnd + 1).trim()
        val returnType = if (afterParams.startsWith(":")) {
            afterParams.removePrefix(":").trim()
                .replace(Regex("""\s*[{=].*$"""), "")
                .trim()
                .ifEmpty { null }
        } else null

        val parameters = if (paramsString.isEmpty()) emptyList() else parseParameters(paramsString)

        return MethodInfo(name, parameters, returnType, isSuspend)
    }

    private fun parseParameters(paramsString: String): List<ParameterInfo> {
        return splitParameters(paramsString).mapNotNull { paramStr ->
            val cleaned = paramStr.trim()
                .replace(Regex("""@\w+(\([^)]*\))?\s*"""), "")
                .trim()

            val match = Regex("""^(\w+)\s*:\s*(.+?)(?:\s*=\s*(.+))?$""").find(cleaned)
            if (match != null) {
                ParameterInfo(
                    name = match.groupValues[1],
                    type = match.groupValues[2].trim(),
                    defaultValue = match.groupValues[3].trim().ifEmpty { null }
                )
            } else null
        }
    }

    private fun splitParameters(paramsString: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var angleDepth = 0
        var parenDepth = 0
        var braceDepth = 0

        for (c in paramsString) {
            when (c) {
                '<' -> angleDepth++
                '>' -> angleDepth--
                '(' -> parenDepth++
                ')' -> parenDepth--
                '{' -> braceDepth++
                '}' -> braceDepth--
                ',' -> {
                    if (angleDepth == 0 && parenDepth == 0 && braceDepth == 0) {
                        result.add(current.toString())
                        current.clear()
                        continue
                    }
                }
            }
            current.append(c)
        }
        if (current.isNotBlank()) result.add(current.toString())
        return result
    }

    private fun findMatchingParen(s: String, openIndex: Int): Int {
        var depth = 0
        for (i in openIndex until s.length) {
            when (s[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun isNonPublic(trimmedLine: String): Boolean =
        trimmedLine.startsWith("private ") ||
                trimmedLine.startsWith("protected ") ||
                trimmedLine.startsWith("internal ")
}
