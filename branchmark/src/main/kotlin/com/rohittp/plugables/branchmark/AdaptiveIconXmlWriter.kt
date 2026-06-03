package com.rohittp.plugables.branchmark

/**
 * Builds the debug `<adaptive-icon>` XML: the `<foreground>` is rewritten to point at the generated
 * stamped mipmap, while the original `<background>` and `<monochrome>` references are preserved.
 */
object AdaptiveIconXmlWriter {

    fun buildXml(refs: AdaptiveIconRefs, foregroundResourceName: String): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("""<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">""")
        if (refs.background != null) {
            appendLine("""    <background android:drawable="@${refs.background.type}/${refs.background.name}" />""")
        }
        appendLine("""    <foreground android:drawable="@mipmap/$foregroundResourceName" />""")
        if (refs.monochrome != null) {
            appendLine("""    <monochrome android:drawable="@${refs.monochrome.type}/${refs.monochrome.name}" />""")
        }
        append("""</adaptive-icon>""")
        appendLine()
    }
}
