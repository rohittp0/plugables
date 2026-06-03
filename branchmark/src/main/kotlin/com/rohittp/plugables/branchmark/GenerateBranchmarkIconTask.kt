package com.rohittp.plugables.branchmark

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.imageio.ImageIO

/**
 * Reads the app's existing adaptive launcher icon and writes a stamped debug variant: per density a
 * `mipmap-<dpi>/<foregroundResourceName>.png` with the branch banner, plus `mipmap-anydpi-v26`
 * adaptive XML(s) pointing the foreground at the stamped image while preserving background/monochrome.
 *
 * Cacheable: all inputs are declared, parsing/resolution of the icon graph happens here at execution
 * time (keeping configuration-cache compatibility), and the output is a relocatable directory.
 */
@CacheableTask
abstract class GenerateBranchmarkIconTask : DefaultTask() {

    @get:Input
    abstract val branch: Property<String>

    @get:Input
    abstract val launcherIconName: Property<String>

    @get:Input
    abstract val foregroundResourceName: Property<String>

    @get:Input
    abstract val ribbonColor: Property<String>

    @get:Input
    abstract val ribbonTextColor: Property<String>

    @get:Input
    abstract val fallbackRibbonText: Property<String>

    @get:Input
    abstract val defaultEmoji: Property<String>

    @get:Input
    abstract val densities: ListProperty<String>

    @get:Input
    abstract val emojiByPrefix: MapProperty<String, String>

    /** Launcher-icon resource files that, when changed, should re-stamp. Fingerprinted for caching. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val iconInputs: ConfigurableFileCollection

    /** Root of the main `res` directory; used only to resolve the icon graph at execution time. */
    @get:Internal
    abstract val mainResDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val info = BranchInfo.parse(branch.get())
        val resDir = mainResDir.get().asFile
        val iconName = launcherIconName.get()
        val fgName = foregroundResourceName.get()

        val refs = LauncherIconReader.read(resDir, iconName)

        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()

        // Resolve the prefix emoji once (detectable branches only).
        val emoji = if (!info.undetectable && info.prefix != null) {
            EmojiResolver.emojiFor(info.prefix, emojiByPrefix.get(), defaultEmoji.get())
        } else null
        val svgBytes = emoji?.let { EmojiResolver.svgBytesFor(it) }
        if (emoji != null && svgBytes == null) {
            logger.warn("[branchmark] No bundled SVG for emoji '$emoji' — falling back to a system-font glyph.")
        }

        for (qualifier in densities.get()) {
            val density = Density.fromQualifier(qualifier)
            if (density == null) {
                logger.warn("[branchmark] Unknown density '$qualifier' — skipping.")
                continue
            }
            val foreground = ForegroundSource.resolve(resDir, refs.foreground, density)
            val composed = IconRenderer.composite(
                foreground = foreground,
                info = info,
                ribbonColorHex = ribbonColor.get(),
                ribbonTextColorHex = ribbonTextColor.get(),
                fallbackText = fallbackRibbonText.get(),
                emojiSvgBytes = svgBytes,
                emojiFallbackGlyph = emoji,
            )
            val densityDir = File(out, density.mipmapDir()).apply { mkdirs() }
            ImageIO.write(composed, "png", File(densityDir, "$fgName.png"))
        }

        // Generate the debug adaptive XML(s) referencing the stamped foreground.
        val anydpi = File(out, "mipmap-anydpi-v26").apply { mkdirs() }
        File(anydpi, "$iconName.xml").writeText(AdaptiveIconXmlWriter.buildXml(refs, fgName))
        if (LauncherIconReader.hasRound(resDir, iconName)) {
            val roundRefs = LauncherIconReader.read(resDir, "${iconName}_round")
            File(anydpi, "${iconName}_round.xml").writeText(AdaptiveIconXmlWriter.buildXml(roundRefs, fgName))
        }

        val mode = if (info.undetectable) "fallback '${fallbackRibbonText.get()}'" else "'${info.displaySuffix}' + $emoji"
        logger.lifecycle("[branchmark] Stamped $iconName debug icon for branch '${info.raw}' ($mode).")
    }
}
