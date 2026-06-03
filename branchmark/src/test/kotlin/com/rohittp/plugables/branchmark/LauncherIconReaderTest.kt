package com.rohittp.plugables.branchmark

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class LauncherIconReaderTest {

    @Test
    fun `reads foreground, background and monochrome refs`(@TempDir tmp: File) {
        val res = File(tmp, "res").apply { mkdirs() }
        IconFixtures.writeAdaptiveIconSet(res)

        val refs = LauncherIconReader.read(res, "ic_launcher")
        assertEquals(ResourceRef("drawable", "ic_launcher_foreground"), refs.foreground)
        assertEquals(ResourceRef("drawable", "ic_launcher_background"), refs.background)
        assertEquals(ResourceRef("drawable", "ic_launcher_foreground"), refs.monochrome)
    }

    @Test
    fun `detects presence and absence of round icon`(@TempDir tmp: File) {
        val res = File(tmp, "res").apply { mkdirs() }
        IconFixtures.writeAdaptiveIconSet(res, round = true)
        assertTrue(LauncherIconReader.hasRound(res, "ic_launcher"))

        val res2 = File(tmp, "res2").apply { mkdirs() }
        IconFixtures.writeAdaptiveIconSet(res2, round = false)
        assertFalse(LauncherIconReader.hasRound(res2, "ic_launcher"))
    }

    @Test
    fun `missing adaptive xml fails clearly`(@TempDir tmp: File) {
        val res = File(tmp, "res").apply { mkdirs() }
        val ex = assertFailsWith<IllegalStateException> { LauncherIconReader.read(res, "ic_launcher") }
        assertTrue(ex.message!!.contains("not found"))
    }

    @Test
    fun `background omitted when absent`(@TempDir tmp: File) {
        val res = File(tmp, "res").apply { mkdirs() }
        File(res, "mipmap-anydpi-v26").mkdirs()
        File(res, "mipmap-anydpi-v26/ic_launcher.xml").writeText(
            """
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <foreground android:drawable="@mipmap/fg" />
            </adaptive-icon>
            """.trimIndent()
        )
        val refs = LauncherIconReader.read(res, "ic_launcher")
        assertEquals(ResourceRef("mipmap", "fg"), refs.foreground)
        assertNull(refs.background)
        assertNull(refs.monochrome)
    }
}
