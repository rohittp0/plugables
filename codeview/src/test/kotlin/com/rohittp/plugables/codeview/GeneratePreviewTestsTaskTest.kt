package com.rohittp.plugables.codeview

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratePreviewTestsTaskTest {

    private fun setupSinglePreview(tmp: File, fileContent: String = singlePreviewSrc): GeneratePreviewTestsTask {
        val project = ProjectBuilder.builder().withProjectDir(tmp).build()
        val sources = File(tmp, "src/main/kotlin/com/example/app").apply { mkdirs() }
        File(sources, "Home.kt").writeText(fileContent)
        return project.tasks.register("gen", GeneratePreviewTestsTask::class.java) {
            sourceDirs.from(File(tmp, "src/main/kotlin"))
            testsOutputDir.set(File(tmp, "out/tests"))
            sidecarOutputDir.set(File(tmp, "out/sidecars"))
            indexOutputFile.set(File(tmp, "out/preview-index.json"))
            testActivityClass.set("com.example.app.MainActivity")
            testMode.set("unit")
            onDeviceSidecarSubdir.set("codeview")
            previousSidecarsDir.set(File(tmp, "out/prev-sidecars"))
        }.get()
    }

    private fun generatedTestFile(tmp: File): File {
        val generatedDir = File(tmp, "out/tests/com/rohittp/plugables/codeview/generated")
        return generatedDir.listFiles { f ->
            f.name.startsWith("Codeview_HomeScreenPreview_") && f.name.endsWith("Test.kt")
        }!!.single()
    }

    @Test
    fun `no previousSidecarsDir means no Ignore is emitted`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val task = setupSinglePreview(tmp)
        task.generate()
        val text = generatedTestFile(tmp).readText()
        assertFalse(text.contains("@Ignore"), "no @Ignore expected when no previous sidecar exists")
        assertTrue(text.contains("previewSourceHash = \""), "previewSourceHash arg should be present")
    }

    @Test
    fun `matching sidecar marks the test as Ignored`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val task = setupSinglePreview(tmp)
        // First pass generates so we can read out the source hash.
        task.generate()
        val firstText = generatedTestFile(tmp).readText()
        val hash = Regex("""previewSourceHash = "([0-9a-f]+)"""").find(firstText)!!.groupValues[1]
        val previewId = generatedTestFile(tmp).nameWithoutExtension.removeSuffix("Test")

        val prev = File(tmp, "out/prev-sidecars").apply { mkdirs() }
        File(prev, "$previewId.json").writeText(
            """{"schemaVersion":2,"id":"$previewId","sourceHash":"$hash","imageWidth":0,"imageHeight":0,"renderedTexts":[],"nodes":[]}"""
        )

        task.generate()
        val text = generatedTestFile(tmp).readText()
        assertTrue(text.contains("@Ignore(\"codeview: source unchanged since last render\")"))
        assertTrue(text.contains("import org.junit.Ignore"))
    }

    @Test
    fun `mismatched sourceHash leaves the test enabled`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val task = setupSinglePreview(tmp)
        task.generate()
        val previewId = generatedTestFile(tmp).nameWithoutExtension.removeSuffix("Test")

        val prev = File(tmp, "out/prev-sidecars").apply { mkdirs() }
        File(prev, "$previewId.json").writeText(
            """{"schemaVersion":2,"id":"$previewId","sourceHash":"deadbeef","imageWidth":0,"imageHeight":0,"renderedTexts":[],"nodes":[]}"""
        )

        task.generate()
        assertFalse(generatedTestFile(tmp).readText().contains("@Ignore"))
    }

    @Test
    fun `v1 schema sidecar is treated as stale`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val task = setupSinglePreview(tmp)
        task.generate()
        val firstText = generatedTestFile(tmp).readText()
        val hash = Regex("""previewSourceHash = "([0-9a-f]+)"""").find(firstText)!!.groupValues[1]
        val previewId = generatedTestFile(tmp).nameWithoutExtension.removeSuffix("Test")

        val prev = File(tmp, "out/prev-sidecars").apply { mkdirs() }
        // Even though the hash matches, schemaVersion=1 means we re-render.
        File(prev, "$previewId.json").writeText(
            """{"schemaVersion":1,"id":"$previewId","sourceHash":"$hash","imageWidth":0,"imageHeight":0,"renderedTexts":[],"nodes":[]}"""
        )

        task.generate()
        assertFalse(generatedTestFile(tmp).readText().contains("@Ignore"))
    }

    private companion object {
        val singlePreviewSrc = """
            package com.example.app
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview
            @Composable
            fun HomeScreenPreview() { }
        """.trimIndent()
    }

    @Test
    fun `generates one test class per preview and an index json`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val project = ProjectBuilder.builder().withProjectDir(tmp).build()
        val sources = File(tmp, "src/main/kotlin/com/example/app").apply { mkdirs() }
        File(sources, "Home.kt").writeText("""
            package com.example.app
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview
            @Composable
            fun HomeScreenPreview() { }
        """.trimIndent())

        val task = project.tasks.register("gen", GeneratePreviewTestsTask::class.java) {
            sourceDirs.from(File(tmp, "src/main/kotlin"))
            testsOutputDir.set(File(tmp, "out/tests"))
            sidecarOutputDir.set(File(tmp, "out/sidecars"))
            indexOutputFile.set(File(tmp, "out/preview-index.json"))
            testActivityClass.set("com.example.app.MainActivity")
            testMode.set("unit")
            onDeviceSidecarSubdir.set("codeview")
        }.get()

        task.generate()

        val generatedDir = File(tmp, "out/tests/com/rohittp/plugables/codeview/generated")
        val testFile = generatedDir.listFiles { f ->
            f.name.startsWith("Codeview_HomeScreenPreview_") && f.name.endsWith("Test.kt")
        }?.singleOrNull()
        assertTrue(testFile != null && testFile.exists(), "expected one generated test file for HomeScreenPreview")
        val className = testFile.nameWithoutExtension
        val text = testFile.readText()
        assertTrue(text.contains("class $className"))
        assertTrue(text.contains("com.example.app.HomeScreenPreview()"))
        assertTrue(text.contains("CodeviewRuntime.renderAndCapture"))
        assertTrue(text.contains("@RunWith(AndroidJUnit4::class)"))

        val helper = File(generatedDir, "CodeviewRuntime.kt")
        assertTrue(helper.exists(), "runtime helper missing")

        val previewId = className.removeSuffix("Test")
        val index = File(tmp, "out/preview-index.json").readText()
        assertTrue(index.contains("\"id\":\"$previewId\""))
        assertTrue(index.contains("\"previewFqn\":\"com.example.app.HomeScreenPreview\""))
    }
}
