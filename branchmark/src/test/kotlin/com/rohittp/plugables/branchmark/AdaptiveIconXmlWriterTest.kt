package com.rohittp.plugables.branchmark

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveIconXmlWriterTest {

    @Test
    fun `foreground rewritten, background and monochrome preserved`() {
        val refs = AdaptiveIconRefs(
            foreground = ResourceRef("drawable", "ic_launcher_foreground"),
            background = ResourceRef("drawable", "ic_launcher_background"),
            monochrome = ResourceRef("drawable", "ic_launcher_foreground"),
        )
        val xml = AdaptiveIconXmlWriter.buildXml(refs, "ic_launcher_foreground_debug")

        assertTrue(xml.contains("""<foreground android:drawable="@mipmap/ic_launcher_foreground_debug" />"""))
        assertTrue(xml.contains("""<background android:drawable="@drawable/ic_launcher_background" />"""))
        assertTrue(xml.contains("""<monochrome android:drawable="@drawable/ic_launcher_foreground" />"""))
        assertTrue(xml.contains("<adaptive-icon"))
    }

    @Test
    fun `omits missing background and monochrome`() {
        val refs = AdaptiveIconRefs(
            foreground = ResourceRef("mipmap", "ic_launcher_foreground"),
            background = null,
            monochrome = null,
        )
        val xml = AdaptiveIconXmlWriter.buildXml(refs, "fg_debug")
        assertFalse(xml.contains("background"))
        assertFalse(xml.contains("monochrome"))
        assertTrue(xml.contains("""<foreground android:drawable="@mipmap/fg_debug" />"""))
    }

    @Test
    fun `resource ref parses type and name`() {
        val ref = ResourceRef.parse("@drawable/ic_launcher_background")!!
        assertTrue(ref.type == "drawable" && ref.name == "ic_launcher_background")
        // strips package qualifier
        assertTrue(ResourceRef.parse("@android:color/white")!!.type == "color")
    }
}
