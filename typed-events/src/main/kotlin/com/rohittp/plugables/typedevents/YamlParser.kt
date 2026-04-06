package com.rohittp.plugables.typedevents

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.Locale

object YamlParser {

    fun parse(file: File): List<EventSpec> {
        val raw = Yaml().load<List<Any>>(file.readText())
            ?: return emptyList()

        return raw.map { item ->
            @Suppress("UNCHECKED_CAST")
            val entry = (item as Map<String, Any>).entries.single()
            val eventName = entry.key
            val value = entry.value

            when (value) {
                is String -> {
                    // Shorthand: - event_name: Description
                    EventSpec(
                        eventName = eventName,
                        info = value,
                        function = deriveFunction(eventName),
                        params = emptyMap()
                    )
                }
                is Map<*, *> -> {
                    // Full form
                    @Suppress("UNCHECKED_CAST")
                    val body = value as Map<String, Any>
                    val info = body["info"] as? String
                        ?: error("Event '$eventName' is missing 'info'")
                    val function = body["function"] as? String ?: deriveFunction(eventName)

                    @Suppress("UNCHECKED_CAST")
                    val rawParams = body["params"] as? Map<String, Any> ?: emptyMap()
                    val params = rawParams.mapValues { (paramName, paramValue) ->
                        @Suppress("UNCHECKED_CAST")
                        val paramBody = paramValue as? Map<String, String>
                            ?: error("Param '$paramName' in '$eventName' has invalid format")
                        val paramType = paramBody["type"]
                            ?: error("Param '$paramName' in '$eventName' is missing 'type'")
                        val paramInfo = paramBody["info"]
                            ?: error("Param '$paramName' in '$eventName' is missing 'info'")
                        ParamSpec(type = paramType, info = paramInfo)
                    }

                    EventSpec(
                        eventName = eventName,
                        info = info,
                        function = function,
                        params = params
                    )
                }
                else -> error("Event '$eventName' has unexpected format")
            }
        }
    }

    fun deriveFunction(eventName: String): String {
        val parts = eventName.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) error("Cannot derive valid Kotlin identifier from event name: '$eventName'")
        val camel = parts.mapIndexed { i, part ->
            if (i == 0) part.replaceFirstChar { it.titlecase(Locale.US) }
            else part.replaceFirstChar { it.titlecase(Locale.US) }
        }.joinToString("")
        return "log$camel"
    }

    fun toParamName(key: String): String {
        val parts = key.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "_p"
        var n = parts.mapIndexed { i, part ->
            if (i == 0) part.lowercase(Locale.US)
            else part.replaceFirstChar { it.titlecase(Locale.US) }
        }.joinToString("")
        if (n.first().isDigit()) n = "_$n"
        return n
    }
}
