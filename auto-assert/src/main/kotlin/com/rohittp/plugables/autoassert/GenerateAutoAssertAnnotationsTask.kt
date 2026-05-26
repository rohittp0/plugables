package com.rohittp.plugables.autoassert

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateAutoAssertAnnotationsTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val pkgDir = outputDir.get().asFile.resolve("com/rohittp/plugables/autoassert")
        pkgDir.mkdirs()

        pkgDir.resolve("AssertForAllCalls.kt").writeText(
            """
            package com.rohittp.plugables.autoassert

            import kotlin.reflect.KClass

            @Target(AnnotationTarget.CLASS)
            @Retention(AnnotationRetention.BINARY)
            annotation class AssertForAllCalls(val klass: KClass<*>, val method: String)
            """.trimIndent() + "\n",
        )

        pkgDir.resolve("NoAssert.kt").writeText(
            """
            package com.rohittp.plugables.autoassert

            @Target(AnnotationTarget.FUNCTION)
            @Retention(AnnotationRetention.BINARY)
            annotation class NoAssert
            """.trimIndent() + "\n",
        )
    }
}
