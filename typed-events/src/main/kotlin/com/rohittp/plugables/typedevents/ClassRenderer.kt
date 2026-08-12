package com.rohittp.plugables.typedevents

object ClassRenderer {

    fun renderHandlerFile(): String {
        val sb = StringBuilder()
        sb.appendLine("package com.rohittp.plugables.analytics")
        sb.appendLine()
        sb.appendLine("// GENERATED FILE. Do not edit.")
        sb.appendLine()
        sb.appendLine("/** Native boundary implemented with the supported Firebase and PostHog SDKs. */")
        sb.appendLine("fun interface AnalyticsSink {")
        sb.appendLine("    fun capture(eventName: String, properties: Map<String, Any?>)")
        sb.appendLine("}")
        sb.appendLine()
        sb.appendLine("internal var typedEventSink: AnalyticsSink? = null")
        sb.appendLine()
        sb.appendLine("/** Registers the single process-wide native analytics adapter. */")
        sb.appendLine("fun registerAnalyticsSink(sink: AnalyticsSink) {")
        sb.appendLine("    typedEventSink = sink")
        sb.appendLine("}")
        sb.appendLine()
        sb.appendLine("/**")
        sb.appendLine(" * Backwards-compatible lambda registration for existing Kotlin consumers.")
        sb.appendLine(" *")
        sb.appendLine(" * Call once at app startup, e.g. inside Application.onCreate:")
        sb.appendLine(" *   registerTypedEventHandler(::logEvent)")
        sb.appendLine(" */")
        sb.appendLine("fun registerTypedEventHandler(handler: (eventName: String, params: Map<String, Any?>) -> Unit) {")
        sb.appendLine("    registerAnalyticsSink(AnalyticsSink(handler))")
        sb.appendLine("}")
        sb.appendLine()
        sb.appendLine("internal fun emitTypedEvent(eventName: String, properties: Map<String, Any?>) {")
        sb.appendLine("    checkNotNull(typedEventSink) {")
        sb.appendLine("        \"registerAnalyticsSink() must be called before logging events\"")
        sb.appendLine("    }.capture(eventName, properties)")
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

    fun renderFacadeFile(specFileName: String, events: List<EventSpec>): String {
        val sb = StringBuilder()
        sb.appendLine("package com.rohittp.plugables.analytics")
        sb.appendLine()
        sb.appendLine("// GENERATED FILE. Do not edit.")
        sb.appendLine("// Source: $specFileName")
        sb.appendLine()
        sb.appendLine("/** Kotlin/Native-friendly facade. Swift calls `AnalyticsEvents.shared.log…`. */")
        sb.appendLine("object AnalyticsEvents {")
        events.forEach { event ->
            val paramsDecl = event.params.entries.joinToString(", ") { (key, spec) ->
                "${YamlParser.toParamName(key)}: ${spec.type}"
            }
            val forwarded = event.params.keys.joinToString(", ") { YamlParser.toParamName(it) }
            sb.appendLine(
                "    fun ${event.function}($paramsDecl) = " +
                    "com.rohittp.plugables.analytics.${event.function}($forwarded)",
            )
        }
        sb.appendLine("}")
        return sb.toString()
    }

    fun renderSchemaFile(specFileName: String, events: List<EventSpec>): String {
        val sb = StringBuilder()
        sb.appendLine("package com.rohittp.plugables.analytics")
        sb.appendLine()
        sb.appendLine("// GENERATED FILE. Do not edit.")
        sb.appendLine("// Source: $specFileName")
        sb.appendLine()
        sb.appendLine("/** Literal generated contract used by cross-platform snapshot tests. */")
        sb.appendLine("object AnalyticsEventSchema {")
        sb.appendLine("    val eventNames: List<String> = listOf(")
        events.forEach { event -> sb.appendLine("        \"${event.eventName}\",") }
        sb.appendLine("    )")
        sb.appendLine()
        sb.appendLine("    val propertyNames: Map<String, List<String>> = mapOf(")
        events.forEach { event ->
            val params = event.params.keys.joinToString(", ") { "\"$it\"" }
            sb.appendLine("        \"${event.eventName}\" to listOf($params),")
        }
        sb.appendLine("    )")
        sb.appendLine("}")
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

        if (event.params.isEmpty()) {
            sb.appendLine("    emitTypedEvent(\"${event.eventName}\", emptyMap())")
        } else {
            val mapEntries = event.params.keys.joinToString(", ") { key ->
                "\"$key\" to ${YamlParser.toParamName(key)}"
            }
            sb.appendLine("    emitTypedEvent(\"${event.eventName}\", mapOf($mapEntries))")
        }

        sb.appendLine("}")
    }
}
