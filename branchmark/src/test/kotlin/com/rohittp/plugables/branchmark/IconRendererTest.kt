package com.rohittp.plugables.branchmark

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IconRendererTest {

    private val n = 432
    private fun transparentForeground() = BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB)

    private fun render(branch: String): BufferedImage {
        val info = BranchInfo.parse(branch)
        val emoji = if (!info.undetectable && info.prefix != null) {
            EmojiResolver.emojiFor(info.prefix, EmojiResolver.DEFAULT_EMOJI_BY_PREFIX, EmojiResolver.DEFAULT_EMOJI)
        } else null
        return IconRenderer.composite(
            foreground = transparentForeground(),
            info = info,
            ribbonColorHex = "#D32F2F",
            ribbonTextColorHex = "#FFFFFF",
            fallbackText = "DEBUG",
            emojiSvgBytes = emoji?.let { EmojiResolver.svgBytesFor(it) },
            emojiFallbackGlyph = emoji,
        )
    }

    private fun alpha(img: BufferedImage, x: Int, y: Int) = (img.getRGB(x, y) ushr 24) and 0xFF

    private fun opaqueCountInRect(img: BufferedImage, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        var count = 0
        for (y in y0 until y1) for (x in x0 until x1) if (alpha(img, x, y) > 0) count++
        return count
    }

    @Test
    fun `output is the foreground size`() {
        val out = render("fix/bug")
        assertEquals(n, out.width)
        assertEquals(n, out.height)
    }

    @Test
    fun `ribbon is drawn in the top-right band`() {
        val out = render("fix/bug")
        // A point inside the corner band near the top edge, away from the centered text.
        val rgb = out.getRGB(200, 5)
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        assertTrue(r > 150 && g < 100 && b < 100, "expected ribbon red at (200,5), got rgb=$r,$g,$b")
    }

    @Test
    fun `emoji present for detectable branch, absent for fallback`() {
        // Emoji safe-zone box: x[0.18n..0.50n], y[0.50n..0.82n].
        val x0 = (0.18 * n).toInt(); val x1 = (0.50 * n).toInt()
        val y0 = (0.50 * n).toInt(); val y1 = (0.82 * n).toInt()

        val withEmoji = opaqueCountInRect(render("fix/bug"), x0, y0, x1, y1)
        val noEmoji = opaqueCountInRect(render("main"), x0, y0, x1, y1)

        assertTrue(withEmoji > 100, "expected emoji pixels for fix/bug, got $withEmoji")
        assertEquals(0, noEmoji, "expected no emoji for undetectable branch")
    }

    @Test
    fun `transparency preserved outside banner regions`() {
        // Top-left corner has neither ribbon (top-right) nor emoji (bottom-left).
        assertEquals(0, alpha(render("fix/bug"), 10, 10))
    }

    @Test
    fun `dynamic and fallback renders differ`() {
        val dynamic = render("fix/bug")
        val fallback = render("main")
        var differ = false
        outer@ for (y in 0 until n) for (x in 0 until n) {
            if (dynamic.getRGB(x, y) != fallback.getRGB(x, y)) { differ = true; break@outer }
        }
        assertTrue(differ, "fix/bug and main should render differently")
    }

    @Test
    fun `long suffix does not overflow or crash`() {
        val out = render("feature/this-is-an-absurdly-long-branch-suffix-that-must-be-ellipsized")
        assertEquals(n, out.width)
    }
}
