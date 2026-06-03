package com.rohittp.plugables.branchmark

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads an app's existing adaptive launcher icon from a `res` directory and extracts the
 * foreground/background/monochrome drawable references. Pure JDK XML — no Gradle or Android types —
 * so it is unit-testable in isolation.
 */
object LauncherIconReader {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /** Adaptive-icon definition file for [iconName] under `mipmap-anydpi-v26`. */
    fun adaptiveXmlFile(resDir: File, iconName: String): File =
        File(resDir, "mipmap-anydpi-v26/$iconName.xml")

    /** True when a `<iconName>_round.xml` adaptive definition exists. */
    fun hasRound(resDir: File, iconName: String): Boolean =
        adaptiveXmlFile(resDir, "${iconName}_round").isFile

    /**
     * Parse `mipmap-anydpi-v26/<iconName>.xml` into its drawable references.
     * @throws IllegalStateException if the file is missing or has no `<foreground>` with a drawable.
     */
    fun read(resDir: File, iconName: String): AdaptiveIconRefs {
        val file = adaptiveXmlFile(resDir, iconName)
        check(file.isFile) {
            "[branchmark] Adaptive icon '${file.path}' not found. branchmark reads the app's existing " +
                "adaptive launcher icon ('$iconName') from the main source set — ensure it exists, or set " +
                "branchmark.launcherIconName."
        }
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Defensive hardening against XXE; these are local project files but cost nothing.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }.newDocumentBuilder().parse(file)

        val foreground = drawableOf(doc.documentElement, "foreground")
            ?: error("[branchmark] '${file.path}' has no <foreground android:drawable=...>.")
        val background = drawableOf(doc.documentElement, "background")
        val monochrome = drawableOf(doc.documentElement, "monochrome")
        return AdaptiveIconRefs(foreground, background, monochrome)
    }

    /** Find a direct child `<tag>` of the adaptive-icon and parse its `android:drawable`. */
    private fun drawableOf(root: Element, tag: String): ResourceRef? {
        val nodes = root.getElementsByTagName(tag)
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val raw = el.getAttributeNS(ANDROID_NS, "drawable").ifEmpty { el.getAttribute("android:drawable") }
            val ref = ResourceRef.parse(raw)
            if (ref != null) return ref
        }
        return null
    }
}
