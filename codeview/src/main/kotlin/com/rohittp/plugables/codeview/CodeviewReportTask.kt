package com.rohittp.plugables.codeview

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class CodeviewReportTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val previewIndexFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sidecarsDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val ideScheme: Property<String>

    @get:Input
    abstract val openOnComplete: Property<Boolean>

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val previewsDir = File(out, "previews").apply { mkdirs() }

        val sidecarRoot = sidecarsDir.get().asFile
        val previews = parseIndex(previewIndexFile.get().asFile.readText())
        val sourceIndex = ProjectSourceIndex.build(sourceDirs.files)

        val rendered = mutableListOf<RenderedPreview>()
        val dimensions = mutableMapOf<String, Pair<Int, Int>>()
        val sourceLineCache = mutableMapOf<String, List<String>>()

        for (spec in previews) {
            val sidecarFile = File(sidecarRoot, "${spec.id}.json")
            if (!sidecarFile.exists()) {
                rendered.add(RenderedPreview(spec, imagePath = null, nodes = emptyList()))
                continue
            }
            val parsed = SidecarReader.read(sidecarFile.readText())
            dimensions[spec.id] = parsed.imageWidth to parsed.imageHeight

            val nodes = parsed.nodes.map { raw ->
                val resolvedFile = if (raw.sourceFileName != null)
                    sourceIndex.resolve(raw.sourceFileName, raw.packageHash)
                else null
                val snippet = resolvedFile?.let { path ->
                    val lines = sourceLineCache.getOrPut(path) {
                        runCatching { File(path).readLines() }.getOrDefault(emptyList())
                    }
                    if (raw.line in 1..lines.size) lines[raw.line - 1].trim().ifEmpty { null } else null
                }
                NodeInfo(
                    id = raw.id,
                    name = raw.name,
                    bounds = raw.bounds,
                    source = SourceLocation(file = resolvedFile, line = raw.line),
                    parentId = raw.parentId,
                    codeSnippet = snippet,
                )
            }

            val pngSrc = File(sidecarRoot, "${spec.id}.png")
            val imagePath = if (pngSrc.exists()) {
                pngSrc.copyTo(File(previewsDir, "${spec.id}.png"), overwrite = true)
                "previews/${spec.id}.png"
            } else null

            rendered.add(RenderedPreview(spec, imagePath, nodes))
        }

        val html = HtmlReportRenderer.render(rendered, dimensions, ideScheme.get())
        val indexFile = File(out, "index.html")
        indexFile.writeText(html)

        logger.lifecycle("[codeview] Wrote ${previews.size} preview(s).")
        logger.lifecycle("[codeview] Report:  ${indexFile.absolutePath}")
        logger.lifecycle("[codeview] Open it: file://${indexFile.absolutePath}")

        if (openOnComplete.getOrElse(true)) {
            runCatching { openInBrowser(indexFile) }.onFailure {
                logger.info("[codeview] Could not auto-open report: ${it.message}")
            }
        }
    }

    private fun openInBrowser(file: File) {
        val osName = System.getProperty("os.name", "").lowercase()
        val cmd: List<String> = when {
            osName.contains("mac") -> listOf("open", file.absolutePath)
            osName.contains("win") -> listOf("cmd", "/c", "start", "", file.absolutePath)
            else -> listOf("xdg-open", file.absolutePath)
        }
        ProcessBuilder(cmd).redirectErrorStream(true).start()
    }

    private fun parseIndex(json: String): List<PreviewSpec> {
        val pattern = Regex(
            """\{"id":"([^"]+)","previewFqn":"([^"]+)","displayName":"([^"]+)","sourceFile":"((?:[^"\\]|\\.)*)","sourceLine":(\d+)\}"""
        )
        return pattern.findAll(json).map { m ->
            PreviewSpec(
                id = m.groupValues[1],
                previewFqn = m.groupValues[2],
                displayName = m.groupValues[3],
                source = SourceLocation(
                    file = m.groupValues[4].replace("\\\\", "\\").ifEmpty { null },
                    line = m.groupValues[5].toInt(),
                ),
            )
        }.toList()
    }
}
