package com.rohittp.plugables.codeview

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class CodeviewReportTaskTest {

    @Test
    fun `assembles report from sidecars and pngs`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val project = ProjectBuilder.builder().withProjectDir(tmp).build()

        val sidecarsDirectory = File(tmp, "sidecars").apply { mkdirs() }
        val sourcesDirectory = File(tmp, "src/main/kotlin/com/example/app").apply { mkdirs() }
        File(sourcesDirectory, "Home.kt").writeText("package com.example.app\n")
        val packageHash = "com.example.app".hashCode()

        File(sidecarsDirectory, "P1.json").writeText(
            "{\"schemaVersion\":1,\"id\":\"P1\",\"previewFqn\":\"com.example.app.HomePreview\",\"displayName\":\"Home\"," +
            "\"sourceFile\":\"${File(sourcesDirectory, "Home.kt").absolutePath.replace("\\", "\\\\")}\",\"sourceLine\":7," +
            "\"imageWidth\":480,\"imageHeight\":960," +
            "\"nodes\":[{\"id\":1,\"name\":\"Text\",\"bounds\":{\"x\":1,\"y\":2,\"width\":3,\"height\":4}," +
            "\"sourceFileName\":\"Home.kt\",\"packageHash\":$packageHash,\"line\":12,\"parentId\":null}]}"
        )
        File(sidecarsDirectory, "P1.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

        val outDir = File(tmp, "report")

        val previewIndexFile = File(tmp, "preview-index.json").apply {
            writeText(
                "{\"schemaVersion\":1,\"previews\":[{\"id\":\"P1\",\"previewFqn\":\"com.example.app.HomePreview\"," +
                "\"displayName\":\"Home\",\"sourceFile\":\"${File(sourcesDirectory, "Home.kt").absolutePath.replace("\\", "\\\\")}\"," +
                "\"sourceLine\":7}]}"
            )
        }

        val task = project.tasks.register("report", CodeviewReportTask::class.java) {
            this.previewIndexFile.set(previewIndexFile)
            this.sidecarsDir.set(sidecarsDirectory)
            this.sourceDirs.from(File(tmp, "src/main/kotlin"))
            this.outputDir.set(outDir)
            this.ideScheme.set("idea")
        }.get()

        task.generate()

        val html = File(outDir, "index.html").readText()
        assertTrue(html.contains("\"displayName\":\"Home\""))
        assertTrue(html.contains("\"name\":\"Text\""))
        assertTrue(html.contains("idea://open?file="))
        assertTrue(html.contains("\"imageWidth\":480"))
        assertTrue(File(outDir, "previews/P1.png").exists())
    }
}
