package com.rohittp.plugables.typedevents

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class GenerateTypedEventsTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val specFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val spec = specFile.get().asFile
        require(spec.exists()) { "Spec file not found: ${spec.absolutePath}" }

        val events = YamlParser.parse(spec)
        validate(events)

        val outRoot = outputDir.get().asFile
        outRoot.deleteRecursively()
        outRoot.mkdirs()

        val pkgDir = File(outRoot, "com/rohittp/plugables/analytics").apply { mkdirs() }

        File(pkgDir, "TypedEventHandler.kt").writeText(
            ClassRenderer.renderHandlerFile()
        )
        File(pkgDir, "AnalyticsEvents.kt").writeText(
            ClassRenderer.renderEventsFile(spec.name, events)
        )

        logger.lifecycle("[typed-events] Generated TypedEventHandler and AnalyticsEvents with ${events.size} event(s).")
    }

    private fun validate(events: List<EventSpec>) {
        val seenEventNames = hashSetOf<String>()
        val seenFunctions = hashSetOf<String>()

        events.forEach { event ->
            require(seenEventNames.add(event.eventName)) {
                "Duplicate event name: '${event.eventName}'"
            }
            require(seenFunctions.add(event.function)) {
                "Duplicate function name: '${event.function}'"
            }
            require(event.info.isNotBlank()) {
                "Event '${event.eventName}' is missing 'info'"
            }
            require(isValidKotlinIdentifier(event.function)) {
                "Cannot derive valid Kotlin identifier from event name: '${event.eventName}'"
            }
            event.params.forEach { (paramName, spec) ->
                require(spec.info.isNotBlank()) {
                    "Param '$paramName' in '${event.eventName}' is missing 'info'"
                }
                require(spec.type.isNotBlank()) {
                    "Param '$paramName' in '${event.eventName}' is missing 'type'"
                }
            }
        }
    }

    private fun isValidKotlinIdentifier(name: String): Boolean {
        if (name.isEmpty()) return false
        if (!name.first().isLetter() && name.first() != '_') return false
        return name.all { it.isLetterOrDigit() || it == '_' }
    }
}
