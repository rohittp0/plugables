package com.rohittp.plugables.codeview

import kotlin.test.Test
import kotlin.test.assertTrue

class HtmlReportRendererTest {

    private val previewSpec = PreviewSpec(
        id = "Codeview_HomePreview_0",
        previewFqn = "com.example.HomePreview",
        displayName = "HomePreview",
        source = SourceLocation("/abs/Home.kt", 12),
    )

    private val nodes = listOf(
        NodeInfo(
            id = 1,
            name = "Text",
            bounds = Bounds(10, 20, 100, 40),
            source = SourceLocation("/abs/Home.kt", 18),
            parentId = null,
        )
    )

    @Test
    fun `embeds preview JSON with overlays`() {
        val rendered = listOf(RenderedPreview(previewSpec, "previews/Codeview_HomePreview_0.png", nodes))
        val html = HtmlReportRenderer.render(rendered, imageDimensions = mapOf("Codeview_HomePreview_0" to (1080 to 1920)), ideScheme = "idea")
        assertTrue(html.contains("\"displayName\":\"HomePreview\""))
        assertTrue(html.contains("\"imagePath\":\"previews/Codeview_HomePreview_0.png\""))
        assertTrue(html.contains("\"name\":\"Text\""))
        assertTrue(html.contains("\"x\":10"))
        assertTrue(html.contains("\"openUrl\":\"idea://open?file=/abs/Home.kt\\u0026line=18\""))
        assertTrue(html.contains("\"imageWidth\":1080"))
    }

    @Test
    fun `previews without image emit null imagePath`() {
        val rendered = listOf(RenderedPreview(previewSpec, null, emptyList()))
        val html = HtmlReportRenderer.render(rendered, imageDimensions = emptyMap(), ideScheme = "idea")
        assertTrue(html.contains("\"imagePath\":null"))
    }

    @Test
    fun `template placeholder is fully replaced`() {
        val rendered = listOf(RenderedPreview(previewSpec, null, emptyList()))
        val html = HtmlReportRenderer.render(rendered, imageDimensions = emptyMap(), ideScheme = "idea")
        assertTrue(!html.contains("__CODEVIEW_DATA__"))
    }

    @Test
    fun `script-tag breakouts in displayName are escaped`() {
        val malicious = previewSpec.copy(displayName = "</script><script>alert(1)</script>")
        val rendered = listOf(RenderedPreview(malicious, null, emptyList()))
        val html = HtmlReportRenderer.render(rendered, imageDimensions = emptyMap(), ideScheme = "idea")
        assertTrue(!html.contains("</script><script>alert(1)</script>"))
    }
}
