package com.rohittp.plugables.protoextended

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AndroidResourceRendererTest {

    private fun enum(flags: ResourceFlags, ref: String = "AspectRatio") = ProtoEnumInfo(
        qualifiedName = "ta.$ref",
        kotlinRef = ref,
        kotlinImport = "com.travelanimator.routemap.$ref",
        constantNames = listOf("RATIO_1_1", "RATIO_16_9"),
        metaProperties = emptyList(),
        resourceFlags = flags,
    )

    @Test
    fun `emits banner package and R import`() {
        val out = AndroidResourceRenderer.render(
            "com.example.generated", "com.travelanimator.routemap",
            listOf(enum(ResourceFlags(displayName = true))),
        )
        assertContains(out, "// GENERATED — do not edit. Source: proto enum definitions.")
        assertContains(out, "package com.example.generated")
        assertContains(out, "import com.travelanimator.routemap.R")
    }

    @Test
    fun `displayName flag emits a StringRes property over lowercased constants`() {
        val out = AndroidResourceRenderer.render(
            "com.example.generated", "com.travelanimator.routemap",
            listOf(enum(ResourceFlags(displayName = true))),
        )
        assertContains(out, "import androidx.annotation.StringRes")
        assertContains(out, "@get:StringRes")
        assertContains(out, "val AspectRatio.displayName: Int")
        assertContains(out, "        AspectRatio.RATIO_1_1 -> R.string.ratio_1_1")
        assertContains(out, "        AspectRatio.RATIO_16_9 -> R.string.ratio_16_9")
    }

    @Test
    fun `icon flag emits a DrawableRes property`() {
        val out = AndroidResourceRenderer.render(
            "com.example.generated", "com.travelanimator.routemap",
            listOf(enum(ResourceFlags(icon = true))),
        )
        assertContains(out, "import androidx.annotation.DrawableRes")
        assertContains(out, "@get:DrawableRes")
        assertContains(out, "val AspectRatio.icon: Int")
        assertContains(out, "        AspectRatio.RATIO_1_1 -> R.drawable.ratio_1_1")
    }

    @Test
    fun `only imports the annotation it actually uses`() {
        val stringOnly = AndroidResourceRenderer.render(
            "p", "r", listOf(enum(ResourceFlags(displayName = true))),
        )
        assertFalse(stringOnly.contains("import androidx.annotation.DrawableRes"))

        val drawableOnly = AndroidResourceRenderer.render(
            "p", "r", listOf(enum(ResourceFlags(icon = true))),
        )
        assertFalse(drawableOnly.contains("import androidx.annotation.StringRes"))
    }

    @Test
    fun `skips enums with no flags and does not import them`() {
        val out = AndroidResourceRenderer.render(
            "p", "r", listOf(enum(ResourceFlags())),
        )
        assertFalse(out.contains("val AspectRatio"))
        assertFalse(out.contains("import com.travelanimator.routemap.AspectRatio"))
    }

    @Test
    fun `sorts enums by qualified name regardless of input order`() {
        val a = enum(ResourceFlags(displayName = true), ref = "AspectRatio")
        val z = enum(ResourceFlags(displayName = true), ref = "Zebra")
        assertEquals(
            AndroidResourceRenderer.render("p", "r", listOf(a, z)),
            AndroidResourceRenderer.render("p", "r", listOf(z, a)),
        )
    }

    @Test
    fun `emits header-only file when nothing to generate`() {
        val out = AndroidResourceRenderer.render("com.example.generated", "r", emptyList())
        assertContains(out, "package com.example.generated")
        assertFalse(out.contains("import "))
        assertFalse(out.contains("val "))
    }
}
