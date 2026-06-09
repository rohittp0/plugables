package com.rohittp.plugables.branchmark

import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.ImageTranscoder
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream

/**
 * Pure Java2D + Batik compositing of the branch banner onto an adaptive-icon foreground.
 *
 * The foreground is copied onto a transparent ARGB canvas (the adaptive-icon background layer shows
 * through). A diagonal ribbon is drawn across the top-right corner with the suffix (or fallback text);
 * a color emoji glyph is drawn in the lower-left safe zone for detectable branches. All geometry is
 * expressed as fractions of the canvas size so it scales across densities. No Gradle/Android types.
 */
object IconRenderer {

    // Adaptive-icon safe zone: launchers crop everything outside a centered circle of this radius
    // (fraction of canvas). Anything a user must always see — emoji AND ribbon text — stays inside it.
    private const val SAFE_INSET = 0.18
    private const val SAFE_RADIUS = 0.5 - SAFE_INSET   // radius of the guaranteed-visible circle

    // Ribbon geometry, as fractions of the canvas size. The band bleeds off the top-right corner and
    // is clipped by the mask there — that's fine — but its centerline is pulled in far enough that it
    // crosses the safe circle, so the text (clipped to that circle below) is always visible.
    private const val RIBBON_CENTER = 0.62   // centerline distance (u+v) from the top-right corner
    private const val RIBBON_THICKNESS = 0.20

    init {
        System.setProperty("java.awt.headless", "true")
    }

    /**
     * @param foreground square adaptive foreground at the target density
     * @param info parsed branch; [BranchInfo.undetectable] selects the fallback ribbon (no emoji)
     * @param emojiSvgBytes bundled color SVG for the prefix emoji, or null for system-font fallback
     */
    fun composite(
        foreground: BufferedImage,
        info: BranchInfo,
        ribbonColorHex: String,
        ribbonTextColorHex: String,
        fallbackText: String,
        emojiSvgBytes: ByteArray?,
        emojiFallbackGlyph: String?,
    ): BufferedImage {
        val n = foreground.width
        val canvas = BufferedImage(n, foreground.height, BufferedImage.TYPE_INT_ARGB)
        val g = canvas.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        g.drawImage(foreground, 0, 0, null)

        if (!info.undetectable && info.prefix != null) {
            drawEmoji(g, n, emojiSvgBytes, emojiFallbackGlyph)
        }

        val text = (if (info.undetectable) fallbackText else info.displaySuffix ?: fallbackText).uppercase()
        drawRibbon(g, n, text, parseColor(ribbonColorHex), parseColor(ribbonTextColorHex))

        g.dispose()
        return canvas
    }

    private fun drawRibbon(g: java.awt.Graphics2D, n: Int, rawText: String, ribbon: Color, textColor: Color) {
        val size = n.toDouble()
        val cm = RIBBON_CENTER * size
        val halfSpan = RIBBON_THICKNESS * size * Math.sqrt(2.0) / 2.0
        val c1 = cm - halfSpan
        val c2 = cm + halfSpan

        // Band trapezoid across the top-right corner. Point on line (u+v=c): (n-c, 0) and (n, c),
        // where u = distance from right edge, v = distance from top edge.
        val band = Path2D.Double().apply {
            moveTo(size - c1, 0.0)
            lineTo(size - c2, 0.0)
            lineTo(size, c2)
            lineTo(size, c1)
            closePath()
        }
        g.color = ribbon
        g.fill(band)

        // The text runs along the band centerline (line `x - y = size - cm`). Launcher masks crop the
        // corner, so the only reliably visible stretch of that line is its chord through the safe
        // circle (centered, radius SAFE_RADIUS). Center the text on the foot of the perpendicular from
        // the canvas center to the line — which, by symmetry, is the centerline midpoint — and limit
        // its width to that chord so it can never spill out of the visible icon on any mask shape.
        val cx = size - cm / 2.0
        val cy = cm / 2.0
        val r = SAFE_RADIUS * size
        val distToCenter = (size - cm) / Math.sqrt(2.0)   // distance from canvas center to the centerline
        val chordHalf = if (distToCenter >= r) 0.0 else Math.sqrt(r * r - distToCenter * distToCenter)
        val usableWidth = chordHalf * 2.0 * 0.90          // small padding inside the visible chord
        val maxHeight = RIBBON_THICKNESS * size * 0.62

        val (font, text) = fitText(g, rawText, usableWidth, maxHeight)
        g.font = font
        val fm = g.fontMetrics
        val tw = fm.stringWidth(text)

        val old = g.transform
        g.rotate(Math.PI / 4.0, cx, cy)
        g.color = textColor
        val baseline = cy + (fm.ascent - fm.descent) / 2.0
        g.drawString(text, (cx - tw / 2.0).toFloat(), baseline.toFloat())
        g.transform = old
    }

    /** Shrink the font until [text] fits [maxWidth]/[maxHeight]; ellipsize if it can't even at min size. */
    private fun fitText(g: java.awt.Graphics2D, text: String, maxWidth: Double, maxHeight: Double): Pair<Font, String> {
        val minSize = 6
        var size = maxHeight.toInt().coerceAtLeast(minSize)
        var font = Font(Font.SANS_SERIF, Font.BOLD, size)
        while (size > minSize) {
            font = Font(Font.SANS_SERIF, Font.BOLD, size)
            if (g.getFontMetrics(font).stringWidth(text) <= maxWidth) return font to text
            size--
        }
        font = Font(Font.SANS_SERIF, Font.BOLD, minSize)
        val fm = g.getFontMetrics(font)
        if (fm.stringWidth(text) <= maxWidth) return font to text
        // Ellipsize.
        var t = text
        while (t.length > 1 && fm.stringWidth("$t…") > maxWidth) t = t.dropLast(1)
        return font to "$t…"
    }

    private fun drawEmoji(g: java.awt.Graphics2D, n: Int, svgBytes: ByteArray?, fallbackGlyph: String?) {
        val box = (0.32 * n).toInt()
        val x = (SAFE_INSET * n).toInt()
        val y = n - (SAFE_INSET * n).toInt() - box
        if (svgBytes != null) {
            val img = rasterizeSvg(svgBytes, box)
            if (img != null) {
                g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER)
                g.drawImage(img, x, y, box, box, null)
                return
            }
        }
        // Best-effort system-font fallback (may render monochrome / tofu, documented limitation).
        if (fallbackGlyph != null) {
            val font = Font(Font.SANS_SERIF, Font.PLAIN, box)
            g.font = font
            val fm = g.fontMetrics
            g.color = Color.DARK_GRAY
            g.drawString(fallbackGlyph, x.toFloat(), (y + fm.ascent).toFloat())
        }
    }

    private fun rasterizeSvg(svgBytes: ByteArray, size: Int): BufferedImage? {
        val transcoder = BufferedImageTranscoder().apply {
            addTranscodingHint(ImageTranscoder.KEY_WIDTH, size.toFloat())
            addTranscodingHint(ImageTranscoder.KEY_HEIGHT, size.toFloat())
        }
        return runCatching {
            transcoder.transcode(TranscoderInput(ByteArrayInputStream(svgBytes)), null)
            transcoder.image
        }.getOrNull()
    }

    /** Parse `#RRGGBB` or `#AARRGGBB` into a [Color]. */
    fun parseColor(hex: String): Color {
        val h = hex.trim().removePrefix("#")
        return when (h.length) {
            6 -> Color(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
            8 -> Color(
                h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16),
                h.substring(6, 8).toInt(16), h.substring(0, 2).toInt(16),
            )
            else -> error("[branchmark] Invalid color '$hex' — expected #RRGGBB or #AARRGGBB.")
        }
    }

    /** Captures the rasterized SVG as an ARGB [BufferedImage] instead of writing PNG bytes. */
    private class BufferedImageTranscoder : ImageTranscoder() {
        var image: BufferedImage? = null
        override fun createImage(w: Int, h: Int): BufferedImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        override fun writeImage(img: BufferedImage, output: TranscoderOutput?) {
            image = img
        }
    }
}
