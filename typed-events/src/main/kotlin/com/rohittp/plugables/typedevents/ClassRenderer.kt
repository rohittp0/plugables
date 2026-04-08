package com.rohittp.plugables.typedevents

object ClassRenderer {

    fun renderHandlerFile(): String {
        val sb = StringBuilder()
        sb.appendLine("package com.rohittp.plugables.analytics")
        sb.appendLine()
        sb.appendLine("// GENERATED FILE. Do not edit.")
        sb.appendLine()
        sb.appendLine("internal var typedEventHandler: ((eventName: String, params: Map<String, Any?>) -> Unit)? = null")
        sb.appendLine()
        sb.appendLine("/**")
        sb.appendLine(" * Registers the analytics event handler.")
        sb.appendLine(" *")
        sb.appendLine(" * Call once at app startup, e.g. inside Application.onCreate:")
        sb.appendLine(" *   registerTypedEventHandler(::logEvent)")
        sb.appendLine(" */")
        sb.appendLine("fun registerTypedEventHandler(handler: (eventName: String, params: Map<String, Any?>) -> Unit) {")
        sb.appendLine("    typedEventHandler = handler")
        sb.appendLine("}")
        return sb.toString()
    }

    fun renderEventsFile(specFileName: String, events: List<EventSpec>): String {
        val sb = StringBuilder()
        sb.appendLine("package com.rohittp.plugables.analytics")
        sb.appendLine()
        sb.appendLine("// GENERATED FILE. Do not edit.")
        sb.appendLine("// Source: $specFileName")

        events.forEach { event ->
            sb.appendLine()
            renderFunction(sb, event)
        }

        return sb.toString()
    }

    private fun renderFunction(sb: StringBuilder, event: EventSpec) {
        sb.appendLine("/**")
        sb.appendLine(" * ${event.info}")
        event.params.forEach { (key, spec) ->
            sb.appendLine(" * @param ${YamlParser.toParamName(key)} ${spec.info}")
        }
        sb.appendLine(" */")

        val paramsDecl = event.params.entries.joinToString(", ") { (key, spec) ->
            "${YamlParser.toParamName(key)}: ${spec.type}"
        }
        sb.appendLine("fun ${event.function}($paramsDecl) {")

        sb.appendLine("    assert(typedEventHandler != null) { \"registerTypedEventHandler() must be called before logging events\" }")
        if (event.params.isEmpty()) {
            sb.appendLine("    typedEventHandler?.invoke(\"${event.eventName}\", emptyMap())")
        } else {
            val mapEntries = event.params.keys.joinToString(", ") { key ->
                "\"$key\" to ${YamlParser.toParamName(key)}"
            }
            sb.appendLine("    typedEventHandler?.invoke(\"${event.eventName}\", mapOf($mapEntries))")
        }

        sb.appendLine("}")
    }
}
