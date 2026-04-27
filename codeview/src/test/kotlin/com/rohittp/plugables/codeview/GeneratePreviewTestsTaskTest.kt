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
        }.get()

        task.generate()

        val testFile = File(tmp, "out/tests/com/rohittp/plugables/codeview/generated/Codeview_HomeScreenPreview_0Test.kt")
        assertTrue(testFile.exists())
        val text = testFile.readText()
        assertTrue(text.contains("class Codeview_HomeScreenPreview_0Test"))
        assertTrue(text.contains("com.example.app.HomeScreenPreview()"))
        assertTrue(text.contains("CodeviewRuntime.renderAndCapture"))
        assertTrue(text.contains("@RunWith(AndroidJUnit4::class)"))

        val helper = File(tmp, "out/tests/com/rohittp/plugables/codeview/generated/CodeviewRuntime.kt")
        assertTrue(helper.exists(), "runtime helper missing")

        val index = File(tmp, "out/preview-index.json").readText()
        assertTrue(index.contains("\"id\":\"Codeview_HomeScreenPreview_0\""))
        assertTrue(index.contains("\"previewFqn\":\"com.example.app.HomeScreenPreview\""))
    }
}
