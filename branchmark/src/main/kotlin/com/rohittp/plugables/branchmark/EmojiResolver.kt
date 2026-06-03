package com.rohittp.plugables.branchmark

/**
 * Maps a branch prefix to an emoji, and an emoji to the bytes of a bundled color SVG (Twemoji).
 *
 * The public DSL works in terms of emoji strings; this resolver turns an emoji into its Unicode
 * codepoint sequence and loads the matching bundled SVG (`branchmark/emoji/<hex>.svg`). If no SVG is
 * bundled for an emoji, [svgBytesFor] returns null and the renderer falls back to a system-font glyph.
 */
object EmojiResolver {

    /** Built-in prefix -> emoji map. Also used as a fallback when a user clears the DSL convention. */
    val DEFAULT_EMOJI_BY_PREFIX: Map<String, String> = linkedMapOf(
        "feat" to "✨",
        "fix" to "🔧",
        "bug" to "🐛",
        "hotfix" to "🚑",
        "chore" to "🧹",
        "refactor" to "♻️",
        "docs" to "📝",
        "test" to "🧪",
        "perf" to "⚡",
        "ci" to "🔁",
        "claude" to "🤖",
    )

    const val DEFAULT_EMOJI: String = "🌿"

    /** Resolve the emoji for [prefix] from [configured] (merged over defaults), else [defaultEmoji]. */
    fun emojiFor(prefix: String, configured: Map<String, String>, defaultEmoji: String): String {
        val key = prefix.trim().lowercase()
        return configured[key] ?: DEFAULT_EMOJI_BY_PREFIX[key] ?: defaultEmoji
    }

    /** Twemoji-style filename for an emoji: lowercase hex codepoints joined by '-', VS16 (fe0f) stripped. */
    fun codepointHex(emoji: String): String =
        emoji.codePoints().toArray()
            .filter { it != 0xfe0f }
            .joinToString("-") { Integer.toHexString(it) }

    /** Bundled SVG bytes for [emoji], or null if none is bundled. */
    fun svgBytesFor(emoji: String): ByteArray? {
        val hex = codepointHex(emoji)
        if (hex.isEmpty()) return null
        val path = "branchmark/emoji/$hex.svg"
        return EmojiResolver::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
    }
}
