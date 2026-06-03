package com.rohittp.plugables.branchmark

/**
 * Android launcher-icon density buckets and the adaptive-icon foreground pixel size for each.
 *
 * Adaptive-icon foregrounds are authored on a 108dp × 108dp canvas, so the pixel size per bucket is
 * `108 * (dpi / 160)`. Rasterizing a vector foreground at this size yields the full foreground at the
 * correct scale; the launcher then masks it, keeping only the inner safe zone.
 */
enum class Density(val qualifier: String, val px: Int) {
    MDPI("mdpi", 108),
    HDPI("hdpi", 162),
    XHDPI("xhdpi", 216),
    XXHDPI("xxhdpi", 324),
    XXXHDPI("xxxhdpi", 432);

    /** Resource folder name for this density bucket, e.g. `mipmap-xxxhdpi`. */
    fun mipmapDir(): String = "mipmap-$qualifier"

    companion object {
        /** Parse a density qualifier (`"xxxhdpi"`) into a [Density], or null if unknown. */
        fun fromQualifier(qualifier: String): Density? =
            entries.firstOrNull { it.qualifier == qualifier.trim().lowercase() }
    }
}
