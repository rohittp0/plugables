package com.rohittp.plugables.viewmodelstub

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GenerateViewModelStubsTask : DefaultTask() {

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val outRoot = outputDir.get().asFile
        outRoot.deleteRecursively()
        outRoot.mkdirs()

        // Always generate the @ViewModelStub annotation class
        val annotationFile = File(outRoot, "com/rohittp/plugables/viewmodelstub/ViewModelStub.kt")
        annotationFile.parentFile.mkdirs()
        annotationFile.writeText(
            """
            package com.rohittp.plugables.viewmodelstub

            @Target(AnnotationTarget.CLASS)
            @Retention(AnnotationRetention.SOURCE)
            annotation class ViewModelStub
            """.trimIndent()
        )

        val srcRoot = sourceDir.get().asFile
        val kotlinFiles = srcRoot.walkTopDown().filter { it.extension == "kt" }
        var generated = 0

        for (file in kotlinFiles) {
            val info = KotlinSourceParser.parseFile(file) ?: continue

            val pkgDir = File(outRoot, info.packageName.replace('.', '/'))
            pkgDir.mkdirs()

            val interfaceFile = File(pkgDir, "${info.interfaceName}.kt")
            interfaceFile.writeText(StubRenderer.renderInterface(info))
            logger.lifecycle("  ✓ Generated interface: ${info.packageName}.${info.interfaceName}")

            val stubFile = File(pkgDir, "${info.stubClassName}.kt")
            stubFile.writeText(StubRenderer.renderStub(info))
            logger.lifecycle("  ✓ Generated stub:      ${info.packageName}.${info.stubClassName}")

            generated++
        }

        if (generated == 0) {
            logger.lifecycle("  No @ViewModelStub classes found.")
        } else {
            logger.lifecycle("  Generated interfaces + stubs for $generated ViewModel(s).")
        }
    }
}
