package com.rohittp.plugables.branchmark

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/**
 * DSL for the `branchmark { }` block. All properties have conventions, so applying the plugin with no
 * configuration produces a sensible result. Configure additive emoji overrides with
 * `emojiByPrefix.put("myprefix", "🌟")` (calling `.set(...)` replaces the built-in defaults — but
 * [EmojiResolver] still falls back to its internal default map for known prefixes).
 */
abstract class BranchmarkExtension {

    /** Build type whose launcher icon is stamped. Default `"debug"`. */
    abstract val buildType: Property<String>

    /** Density buckets to generate. Default `hdpi, xhdpi, xxhdpi, xxxhdpi`. */
    abstract val densities: ListProperty<String>

    /** Name of the existing adaptive launcher icon to read from the main source set. Default `"ic_launcher"`. */
    abstract val launcherIconName: Property<String>

    /** Resource name of the generated stamped foreground. Default `"ic_launcher_foreground_debug"`. */
    abstract val foregroundResourceName: Property<String>

    /** Prefix -> emoji overrides, merged over the built-in defaults. */
    abstract val emojiByPrefix: MapProperty<String, String>

    /** Emoji for unknown prefixes. Default `"🌿"`. */
    abstract val defaultEmoji: Property<String>

    /** Ribbon fill color (`#RRGGBB` or `#AARRGGBB`). Default red `#D32F2F`. */
    abstract val ribbonColor: Property<String>

    /** Ribbon text color. Default white `#FFFFFF`. */
    abstract val ribbonTextColor: Property<String>

    /** Explicit branch override; highest precedence when set. */
    abstract val branchOverride: Property<String>

    /** Ribbon text for slashless / undetectable branches (e.g. `main`, detached HEAD). Default `"DEBUG"`. */
    abstract val fallbackRibbonText: Property<String>

    init {
        buildType.convention("debug")
        densities.convention(listOf("hdpi", "xhdpi", "xxhdpi", "xxxhdpi"))
        launcherIconName.convention("ic_launcher")
        foregroundResourceName.convention("ic_launcher_foreground_debug")
        emojiByPrefix.convention(EmojiResolver.DEFAULT_EMOJI_BY_PREFIX)
        defaultEmoji.convention(EmojiResolver.DEFAULT_EMOJI)
        ribbonColor.convention("#D32F2F")
        ribbonTextColor.convention("#FFFFFF")
        fallbackRibbonText.convention("DEBUG")
    }
}
