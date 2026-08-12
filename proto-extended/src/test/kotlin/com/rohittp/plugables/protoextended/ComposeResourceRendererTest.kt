package com.rohittp.plugables.protoextended

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ComposeResourceRendererTest {

    private fun enum(flags: ResourceFlags, ref: String = "AspectRatio") = ProtoEnumInfo(
        qualifiedName = "ta.$ref",
        kotlinRef = ref,
        kotlinImport = "com.travelanimator.routemap.$ref",
        constantNames = listOf("RATIO_1_1", "RATIO_16_9"),
        metaProperties = emptyList(),
        resourceFlags = flags,
    )

    @Test
    fun `emits banner and the Res package`() {
        val out = ComposeResourceRenderer.render(
            "com.example.generated.resources",
            listOf(enum(ResourceFlags(displayName = true))),
        )
        assertContains(out, "// GENERATED — do not edit. Source: proto enum definitions.")
        assertContains(out, "package com.example.generated.resources")
        assertFalse(out.contains("androidx"))
        assertFalse(out.contains("import com.example.app.R"))
    }

    @Test
    fun `displayName returns a Compose StringResource`() {
        val out = ComposeResourceRenderer.render(
            "com.example.generated.resources",
            listOf(enum(ResourceFlags(displayName = true))),
        )
        assertContains(out, "import org.jetbrains.compose.resources.StringResource")
        assertContains(out, "val AspectRatio.displayName: StringResource")
        assertContains(out, "AspectRatio.RATIO_1_1 -> Res.string.ratio_1_1")
        assertContains(out, "AspectRatio.RATIO_16_9 -> Res.string.ratio_16_9")
    }

    @Test
    fun `icon returns a Compose DrawableResource`() {
        val out = ComposeResourceRenderer.render(
            "com.example.generated.resources",
            listOf(enum(ResourceFlags(icon = true))),
        )
        assertContains(out, "import org.jetbrains.compose.resources.DrawableResource")
        assertContains(out, "val AspectRatio.icon: DrawableResource")
        assertContains(out, "AspectRatio.RATIO_1_1 -> Res.drawable.ratio_1_1")
    }

    @Test
    fun `only imports the resource type it actually uses`() {
        val stringOnly = ComposeResourceRenderer.render(
            "p", listOf(enum(ResourceFlags(displayName = true))),
        )
        assertFalse(stringOnly.contains("DrawableResource"))

        val drawableOnly = ComposeResourceRenderer.render(
            "p", listOf(enum(ResourceFlags(icon = true))),
        )
        assertFalse(drawableOnly.contains("StringResource"))
    }

    @Test
    fun `skips enums with no flags and does not import them`() {
        val out = ComposeResourceRenderer.render("p", listOf(enum(ResourceFlags())))
        assertFalse(out.contains("val AspectRatio"))
        assertFalse(out.contains("import com.travelanimator.routemap.AspectRatio"))
    }

    @Test
    fun `sorts enums by qualified name regardless of input order`() {
        val a = enum(ResourceFlags(displayName = true), ref = "AspectRatio")
        val z = enum(ResourceFlags(displayName = true), ref = "Zebra")
        assertEquals(
            ComposeResourceRenderer.render("p", listOf(a, z)),
            ComposeResourceRenderer.render("p", listOf(z, a)),
        )
    }

    @Test
    fun `emits header-only file when nothing is opted in`() {
        val out = ComposeResourceRenderer.render("com.example.generated.resources", emptyList())
        assertContains(out, "package com.example.generated.resources")
        assertFalse(out.contains("import "))
        assertFalse(out.contains("val "))
    }
}
