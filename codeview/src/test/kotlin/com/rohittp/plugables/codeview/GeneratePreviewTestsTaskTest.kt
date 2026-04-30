package com.rohittp.plugables.codeview

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class GeneratePreviewTestsTaskTest {

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
