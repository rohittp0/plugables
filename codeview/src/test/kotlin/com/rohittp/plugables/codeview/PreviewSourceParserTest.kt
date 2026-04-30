package com.rohittp.plugables.codeview

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewSourceParserTest {

    private fun parseString(content: String): List<PreviewSpec> = parseFull(content).specs

    private fun parseFull(content: String): PreviewSourceParser.ParseResult {
        val tmp = File.createTempFile("preview-${System.nanoTime()}", "Sample.kt").apply {
            writeText(content)
            deleteOnExit()
        }
        return PreviewSourceParser.parseFile(tmp)
    }

    @Test
    fun `single @Preview function is extracted with file and line`() {
        val previews = parseString("""
            package com.example.app

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview
            @Composable
            fun HomeScreenPreview() { }
        """.trimIndent())
        assertEquals(1, previews.size)
        val p = previews[0]
        assertEquals("com.example.app.HomeScreenPreview", p.previewFqn)
        assertEquals("HomeScreenPreview", p.displayName)
        assertEquals(6, p.source.line)
    }

    @Test
    fun `multiple @Preview functions get unique ids`() {
        val previews = parseString("""
            package com.example.app
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview @Composable fun A() { }
            @Preview @Composable fun B() { }
        """.trimIndent())
        assertEquals(2, previews.size)
        assertEquals(setOf("A", "B"), previews.map { it.displayName }.toSet())
        assertTrue(previews.map { it.id }.toSet().size == 2)
    }

    @Test
    fun `@Preview with arguments is detected`() {
        val previews = parseString("""
            package com.example.app
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview(name = "Dark", showBackground = true)
            @Composable
            fun Dark() { }
        """.trimIndent())
        assertEquals(1, previews.size)
        assertEquals("Dark", previews[0].displayName)
    }

    @Test
    fun `@Preview inside line comment is ignored`() {
        val previews = parseString("""
            package com.example.app
            // @Preview annotation reference
            fun notAPreview() { }
        """.trimIndent())
        assertEquals(0, previews.size)
    }

    @Test
    fun `sourceHash is populated, deterministic, and shared across previews from one file`() {
        val result = parseFull("""
            package com.example.app
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview @Composable fun A() { }
            @Preview @Composable fun B() { }
        """.trimIndent())
        assertEquals(2, result.specs.size)
        val a = result.specs[0].sourceHash
        val b = result.specs[1].sourceHash
        assertTrue(a.isNotBlank() && a.matches(Regex("[0-9a-f]{64}")), "expected hex SHA-256, got '$a'")
        assertEquals(a, b, "previews from the same file must share the same sourceHash")
    }

    @Test
    fun `sourceHash differs across files with different content`() {
        val r1 = parseFull("""
            package com.example.app
            @Preview @Composable fun X() { }
        """.trimIndent())
        val r2 = parseFull("""
            package com.example.app
            @Preview @Composable fun X() { /* different body */ }
        """.trimIndent())
        assertEquals(1, r1.specs.size); assertEquals(1, r2.specs.size)
        assertTrue(r1.specs[0].sourceHash != r2.specs[0].sourceHash)
    }

    @Test
    fun `private @Preview functions are skipped and reported`() {
        val result = parseFull("""
            package com.example.app
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview
            @Composable
            private fun Hidden() { }

            @Preview
            @Composable
            fun Visible() { }
        """.trimIndent())
        assertEquals(listOf("Visible"), result.specs.map { it.displayName })
        assertEquals(listOf("com.example.app.Hidden"), result.skippedPrivate)
    }

    @Test
    fun `file without package returns empty`() {
        val previews = parseString("""
            @Preview
            @Composable
            fun Orphan() { }
        """.trimIndent())
        assertEquals(0, previews.size)
    }
}
