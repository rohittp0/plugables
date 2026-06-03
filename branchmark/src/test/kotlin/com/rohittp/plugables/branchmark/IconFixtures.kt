package com.rohittp.plugables.branchmark

import java.io.File

/** Shared helpers to scaffold a minimal Android `res` directory with a vector adaptive icon. */
object IconFixtures {

    /** A full-canvas green vector drawable (valid VectorDrawable that VdPreview can rasterize). */
    val FOREGROUND_VECTOR = """
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="108dp" android:height="108dp"
            android:viewportWidth="108" android:viewportHeight="108">
            <path android:fillColor="#3DDC84" android:pathData="M0,0h108v108h-108z" />
        </vector>
    """.trimIndent()

    val BACKGROUND_VECTOR = """
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="108dp" android:height="108dp"
            android:viewportWidth="108" android:viewportHeight="108">
            <path android:fillColor="#FFFFFF" android:pathData="M0,0h108v108h-108z" />
        </vector>
    """.trimIndent()

    fun adaptiveXml(foreground: String = "ic_launcher_foreground") = """
        <?xml version="1.0" encoding="utf-8"?>
        <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
            <background android:drawable="@drawable/ic_launcher_background" />
            <foreground android:drawable="@drawable/$foreground" />
            <monochrome android:drawable="@drawable/$foreground" />
        </adaptive-icon>
    """.trimIndent()

    /** Write a complete vector adaptive icon set (square + optional round) into [resDir]. */
    fun writeAdaptiveIconSet(resDir: File, round: Boolean = true) {
        File(resDir, "drawable").mkdirs()
        File(resDir, "drawable/ic_launcher_foreground.xml").writeText(FOREGROUND_VECTOR)
        File(resDir, "drawable/ic_launcher_background.xml").writeText(BACKGROUND_VECTOR)
        File(resDir, "mipmap-anydpi-v26").mkdirs()
        File(resDir, "mipmap-anydpi-v26/ic_launcher.xml").writeText(adaptiveXml())
        if (round) {
            File(resDir, "mipmap-anydpi-v26/ic_launcher_round.xml").writeText(adaptiveXml())
        }
    }
}
