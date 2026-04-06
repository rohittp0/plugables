package com.rohittp.plugables.typedevents

object ClassRenderer {

    fun render(specFileName: String, events: List<EventSpec>): String {
        val sb = StringBuilder()
        sb.appendLine("package com.rohittp.plugables.analytics")
        sb.appendLine()
        sb.appendLine("// GENERATED FILE. Do not edit.")
        sb.appendLine("// Source: $specFileName")
        sb.appendLine()
        sb.appendLine("abstract class AnalyticsBase {")
        sb.appendLine()
        sb.appendLine("    protected open fun logEvent(eventName: String, params: Map<String, Any?>) = Unit")

        events.forEach { event ->
            sb.appendLine()
            renderMethod(sb, event)
        }

        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderMethod(sb: StringBuilder, event: EventSpec) {
        // KDoc
        sb.appendLine("    /**")
        sb.appendLine("     * ${event.info}")
        event.params.forEach { (key, spec) ->
            sb.appendLine("     * @param ${YamlParser.toParamName(key)} ${spec.info}")
        }
        sb.appendLine("     */")

        // Signature
        val paramsDecl = event.params.entries.joinToString(", ") { (key, spec) ->
            "${YamlParser.toParamName(key)}: ${spec.type}"
        }
        sb.appendLine("    fun ${event.function}($paramsDecl) {")

        // Body
        if (event.params.isEmpty()) {
            sb.appendLine("        logEvent(\"${event.eventName}\", emptyMap())")
        } else {
            val mapEntries = event.params.keys.joinToString(", ") { key ->
                "\"$key\" to ${YamlParser.toParamName(key)}"
            }
            sb.appendLine("        logEvent(\"${event.eventName}\", mapOf($mapEntries))")
        }

        sb.appendLine("    }")
    }
}
