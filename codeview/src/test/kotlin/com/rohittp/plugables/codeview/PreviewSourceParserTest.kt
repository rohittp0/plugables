package com.rohittp.plugables.codeview

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewSourceParserTest {

    private fun parseString(content: String): List<PreviewSpec> {
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
    fun `file without package returns empty`() {
        val previews = parseString("""
            @Preview
            @Composable
            fun Orphan() { }
        """.trimIndent())
        assertEquals(0, previews.size)
    }
}
