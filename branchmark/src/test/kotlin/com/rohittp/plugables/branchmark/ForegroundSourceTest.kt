package com.rohittp.plugables.branchmark

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class ForegroundSourceTest {

    @Test
    fun `rasterizes a vector foreground at the density size`(@TempDir tmp: File) {
        val res = File(tmp, "res").apply { mkdirs() }
        IconFixtures.writeAdaptiveIconSet(res)

        val img = ForegroundSource.resolve(res, ResourceRef("drawable", "ic_launcher_foreground"), Density.XXXHDPI)
        assertEquals(432, img.width)
        assertEquals(432, img.height)

        // The fixture fills the canvas with #3DDC84; the center pixel should be that green.
        val center = img.getRGB(216, 216)
        val r = (center shr 16) and 0xFF
        val g = (center shr 8) and 0xFF
        val b = center and 0xFF
        assertTrue(g > r && g > b, "expected green-dominant center pixel, got rgb=$r,$g,$b")
    }

    @Test
    fun `rasterizes smaller for lower densities`(@TempDir tmp: File) {
        val res = File(tmp, "res").apply { mkdirs() }
        IconFixtures.writeAdaptiveIconSet(res)
        val img = ForegroundSource.resolve(res, ResourceRef("drawable", "ic_launcher_foreground"), Density.HDPI)
        assertEquals(162, img.width)
    }
}
