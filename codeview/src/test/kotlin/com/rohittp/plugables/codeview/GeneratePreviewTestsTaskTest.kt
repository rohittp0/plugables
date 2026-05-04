package com.rohittp.plugables.codeview

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratePreviewTestsTaskTest {

    private fun setupTask(
        tmp: File,
        files: Map<String, String> = mapOf("Home.kt" to singlePreviewSrc),
    ): GeneratePreviewTestsTask {
        val project = ProjectBuilder.builder().withProjectDir(tmp).build()
        val sources = File(tmp, "src/main/kotlin/com/example/app").apply { mkdirs() }
        for ((name, content) in files) File(sources, name).writeText(content)
        return project.tasks.register("gen", GeneratePreviewTestsTask::class.java) {
            sourceDirs.from(File(tmp, "src/main/kotlin"))
            testsOutputDir.set(File(tmp, "out/tests"))
            sidecarOutputDir.set(File(tmp, "out/sidecars"))
            indexOutputFile.set(File(tmp, "out/preview-index.json"))
            testActivityClass.set("com.example.app.MainActivity")
            testMode.set("instrumented")
            onDeviceSidecarSubdir.set("codeview")
            previousSidecarsDir.set(File(tmp, "out/prev-sidecars"))
        }.get()
    }

    private fun generatedDir(tmp: File) = File(tmp, "out/tests/com/rohittp/plugables/codeview/generated")
    private fun batchTest(tmp: File) = File(generatedDir(tmp), "CodeviewBatchTest.kt")
    private fun registry(tmp: File) = File(generatedDir(tmp), "CodeviewPreviewRegistry.kt")
    private fun runtime(tmp: File) = File(generatedDir(tmp), "CodeviewRuntime.kt")

    @Test
    fun `generates a single batch test, registry, and runtime helper`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        setupTask(tmp).generate()

        assertTrue(batchTest(tmp).exists())
        assertTrue(registry(tmp).exists())
        assertTrue(runtime(tmp).exists())

        // No per-preview test classes any more.
        val perPreviewClasses = generatedDir(tmp).listFiles { f ->
            f.name.startsWith("Codeview_") && f.name.endsWith("Test.kt")
        } ?: emptyArray()
        assertEquals(0, perPreviewClasses.size, "no per-preview test classes should be generated")

        val batchText = batchTest(tmp).readText()
        assertTrue(batchText.contains("class CodeviewBatchTest"))
        assertTrue(batchText.contains("fun renderAll()"))
        assertTrue(batchText.contains("CodeviewRuntime.runBatch("))
        assertTrue(batchText.contains("registry = PREVIEW_REGISTRY"))
        assertTrue(batchText.contains("skippedIds = SKIPPED_IDS"))
    }

    @Test
    fun `registry contains every preview with metadata and a composable lambda`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        setupTask(tmp).generate()
        val regText = registry(tmp).readText()
        assertTrue(regText.contains("internal val PREVIEW_REGISTRY"))
        assertTrue(regText.contains("CodeviewRuntime.PreviewMeta("))
        assertTrue(regText.contains("fqn = \"com.example.app.HomeScreenPreview\""))
        assertTrue(regText.contains(") to { com.example.app.HomeScreenPreview() }"))
        // sourceHash is a 64-char hex string.
        assertTrue(Regex("""sourceHash = "[0-9a-f]{64}",""").containsMatchIn(regText))
    }

    @Test
    fun `no previousSidecarsDir means SKIPPED_IDS is empty`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        setupTask(tmp).generate()
        val skipBlock = skippedIdsBlock(registry(tmp).readText())
        assertEquals(emptyList(), skipBlock)
    }

    @Test
    fun `matching sidecar adds preview id to SKIPPED_IDS`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val task = setupTask(tmp)
        task.generate()
        val previewId = previewIdFromRegistry(registry(tmp).readText())
        val hash = sourceHashFromRegistry(registry(tmp).readText())

        File(tmp, "out/prev-sidecars").apply { mkdirs() }
        File(tmp, "out/prev-sidecars/$previewId.json").writeText(usableSidecar(previewId, hash))

        task.generate()
        assertEquals(listOf(previewId), skippedIdsBlock(registry(tmp).readText()))
    }

    @Test
    fun `mismatched sourceHash leaves SKIPPED_IDS empty`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val task = setupTask(tmp)
        task.generate()
        val previewId = previewIdFromRegistry(registry(tmp).readText())

        File(tmp, "out/prev-sidecars").apply { mkdirs() }
        File(tmp, "out/prev-sidecars/$previewId.json").writeText(usableSidecar(previewId, "deadbeef"))

        task.generate()
        assertEquals(emptyList(), skippedIdsBlock(registry(tmp).readText()))
    }

    @Test
    fun `v1 schema sidecar is treated as stale`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val task = setupTask(tmp)
        task.generate()
        val previewId = previewIdFromRegistry(registry(tmp).readText())
        val hash = sourceHashFromRegistry(registry(tmp).readText())

        File(tmp, "out/prev-sidecars").apply { mkdirs() }
        File(tmp, "out/prev-sidecars/$previewId.json").writeText(
            """{"schemaVersion":1,"id":"$previewId","sourceHash":"$hash","imageWidth":1600,"imageHeight":2560,"renderedTexts":[],"nodes":[{"id":1,"name":"Box","bounds":{"x":0,"y":0,"width":10,"height":10},"sourceFileName":null,"packageHash":0,"line":-1,"parentId":null}]}"""
        )

        task.generate()
        assertEquals(emptyList(), skippedIdsBlock(registry(tmp).readText()))
    }

    @Test
    fun `sidecar with imageWidth zero is treated as stale even when sourceHash matches`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val task = setupTask(tmp)
        task.generate()
        val previewId = previewIdFromRegistry(registry(tmp).readText())
        val hash = sourceHashFromRegistry(registry(tmp).readText())

        File(tmp, "out/prev-sidecars").apply { mkdirs() }
        // A silent capture failure: dimensions are 0, no renderError, but no usable bytes.
        File(tmp, "out/prev-sidecars/$previewId.json").writeText(
            """{"schemaVersion":3,"id":"$previewId","sourceHash":"$hash","imageWidth":0,"imageHeight":0,"renderedTexts":[],"nodes":[]}"""
        )

        task.generate()
        assertEquals(emptyList(), skippedIdsBlock(registry(tmp).readText()))
    }

    @Test
    fun `sidecar with empty nodes is treated as stale even when image was captured`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val task = setupTask(tmp)
        task.generate()
        val previewId = previewIdFromRegistry(registry(tmp).readText())
        val hash = sourceHashFromRegistry(registry(tmp).readText())

        File(tmp, "out/prev-sidecars").apply { mkdirs() }
        // Image OK, but Inspectable picked up the wrong CompositionData → no nodes.
        // We must re-render so the user actually gets bounding boxes.
        File(tmp, "out/prev-sidecars/$previewId.json").writeText(
            """{"schemaVersion":3,"id":"$previewId","sourceHash":"$hash","imageWidth":1600,"imageHeight":2560,"renderedTexts":[],"nodes":[]}"""
        )

        task.generate()
        assertEquals(emptyList(), skippedIdsBlock(registry(tmp).readText()))
    }

    @Test
    fun `sidecar with renderError is treated as stale even when sourceHash matches`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val task = setupTask(tmp)
        task.generate()
        val previewId = previewIdFromRegistry(registry(tmp).readText())
        val hash = sourceHashFromRegistry(registry(tmp).readText())

        File(tmp, "out/prev-sidecars").apply { mkdirs() }
        File(tmp, "out/prev-sidecars/$previewId.json").writeText(
            """{"schemaVersion":3,"id":"$previewId","sourceHash":"$hash","imageWidth":0,"imageHeight":0,"renderError":"boom","renderedTexts":[],"nodes":[]}"""
        )

        task.generate()
        // Even though the source hash matches, last run failed → must re-render.
        assertEquals(emptyList(), skippedIdsBlock(registry(tmp).readText()))
    }

    @Test
    fun `excludePreviews filters previews out of the registry by displayName`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val files = mapOf(
            "Home.kt" to singlePreviewSrc,
            "Other.kt" to """
                package com.example.app
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.tooling.preview.Preview

                @Preview
                @Composable
                fun OtherPreview() { }
            """.trimIndent(),
        )
        val task = setupTask(tmp, files).also {
            it.excludePreviews.set(listOf("HomeScreenPreview"))
        }
        task.generate()

        val regText = registry(tmp).readText()
        assertFalse(regText.contains("HomeScreenPreview"), "excluded preview must not be in registry")
        assertTrue(regText.contains("OtherPreview"), "non-excluded preview must remain")
    }

    @Test
    fun `multiple previews across files, only matching one is skipped`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val files = mapOf(
            "Home.kt" to singlePreviewSrc,
            "Other.kt" to """
                package com.example.app
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.tooling.preview.Preview

                @Preview
                @Composable
                fun OtherPreview() { }
            """.trimIndent(),
        )
        val task = setupTask(tmp, files)
        task.generate()
        val regText = registry(tmp).readText()
        // Two preview entries.
        val ids = Regex("""id = "(Codeview_[A-Za-z0-9_]+)",""").findAll(regText)
            .map { it.groupValues[1] }.toList()
        assertEquals(2, ids.size)
        val homeId = ids.first { it.contains("HomeScreenPreview") }
        val homeHash = sourceHashFromRegistry(regText, homeId)

        File(tmp, "out/prev-sidecars").apply { mkdirs() }
        File(tmp, "out/prev-sidecars/$homeId.json").writeText(usableSidecar(homeId, homeHash))

        task.generate()
        assertEquals(listOf(homeId), skippedIdsBlock(registry(tmp).readText()))
    }

    private fun usableSidecar(id: String, hash: String): String =
        """{"schemaVersion":2,"id":"$id","sourceHash":"$hash","imageWidth":1600,"imageHeight":2560,"renderedTexts":[],"nodes":[{"id":1,"name":"Box","bounds":{"x":0,"y":0,"width":10,"height":10},"sourceFileName":null,"packageHash":0,"line":-1,"parentId":null}]}"""

    private fun previewIdFromRegistry(regText: String): String =
        Regex("""id = "(Codeview_[A-Za-z0-9_]+)",""").find(regText)!!.groupValues[1]

    private fun sourceHashFromRegistry(regText: String, expectedId: String? = null): String {
        // Pull the first sourceHash if no id specified, else the one in the entry that matches.
        val entryRegex = Regex(
            """id = "(Codeview_[A-Za-z0-9_]+)",[\s\S]*?sourceHash = "([0-9a-f]+)","""
        )
        for (m in entryRegex.findAll(regText)) {
            val id = m.groupValues[1]
            val hash = m.groupValues[2]
            if (expectedId == null || id == expectedId) return hash
        }
        error("no sourceHash matching id=$expectedId in registry")
    }

    private fun skippedIdsBlock(regText: String): List<String> {
        val block = Regex("""SKIPPED_IDS:\s*Set<String>\s*=\s*setOf\(([\s\S]*?)\)""").find(regText)
            ?.groupValues?.get(1) ?: return emptyList()
        return Regex(""""(Codeview_[A-Za-z0-9_]+)"""").findAll(block).map { it.groupValues[1] }.toList()
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
}
