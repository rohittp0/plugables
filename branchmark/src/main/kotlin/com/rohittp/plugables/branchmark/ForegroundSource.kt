package com.rohittp.plugables.branchmark

import com.android.ide.common.vectordrawable.VdPreview
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Resolves an adaptive-icon foreground [ResourceRef] to a [BufferedImage] at a given [Density].
 *
 * - A **vector** drawable (`drawable/<name>.xml`) is rasterized with Android's own [VdPreview]
 *   (the tool AGP/Asset Studio use) at the full 108dp canvas size for the density.
 * - A **raster** drawable (`mipmap-<dpi>/<name>.png`) is read directly, scaled to the target size if
 *   the exact density bucket is absent.
 */
object ForegroundSource {

    init {
        // VdPreview and Batik both touch AWT; make sure we never try to open a display on CI.
        System.setProperty("java.awt.headless", "true")
    }

    fun resolve(resDir: File, ref: ResourceRef, density: Density): BufferedImage {
        val vector = findVectorXml(resDir, ref)
        if (vector != null) return rasterizeVector(vector, density)

        val raster = findRaster(resDir, ref, density)
        if (raster != null) return scaleTo(ImageIO.read(raster), density.px)

        error(
            "[branchmark] Could not resolve foreground '@${ref.type}/${ref.name}' — no vector " +
                "'${ref.type}/${ref.name}.xml' and no raster '${ref.type}-*/${ref.name}.png' under '${resDir.path}'."
        )
    }

    private fun rasterizeVector(xmlFile: File, density: Density): BufferedImage {
        val log = StringBuilder()
        val image = VdPreview.getPreviewFromVectorXml(
            VdPreview.TargetSize.createFromMaxDimension(density.px),
            xmlFile.readText(),
            log,
        )
        checkNotNull(image) {
            "[branchmark] Failed to rasterize vector '${xmlFile.path}': ${log.toString().ifBlank { "unknown error" }}"
        }
        return image
    }

    /** Locate a vector XML for the ref: the unqualified folder first, then any density-qualified folder. */
    private fun findVectorXml(resDir: File, ref: ResourceRef): File? {
        val direct = File(resDir, "${ref.type}/${ref.name}.xml")
        if (direct.isFile) return direct
        return resDir.listFiles { f -> f.isDirectory && f.name.startsWith("${ref.type}-") }
            ?.map { File(it, "${ref.name}.xml") }
            ?.firstOrNull { it.isFile }
    }

    /** Locate a raster PNG, preferring the exact density bucket, else the highest-resolution available. */
    private fun findRaster(resDir: File, ref: ResourceRef, density: Density): File? {
        val exact = File(resDir, "${ref.type}-${density.qualifier}/${ref.name}.png")
        if (exact.isFile) return exact
        // Highest-resolution bucket available, then the unqualified folder.
        val byDensity = Density.entries.sortedByDescending { it.px }
            .map { File(resDir, "${ref.type}-${it.qualifier}/${ref.name}.png") }
            .firstOrNull { it.isFile }
        if (byDensity != null) return byDensity
        val unqualified = File(resDir, "${ref.type}/${ref.name}.png")
        return unqualified.takeIf { it.isFile }
    }

    private fun scaleTo(src: BufferedImage, size: Int): BufferedImage {
        if (src.width == size && src.height == size) return src
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(src, 0, 0, size, size, null)
        g.dispose()
        return out
    }
}
