package com.rohittp.plugables.branchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmojiResolverTest {

    @Test
    fun `codepoint hex strips variation selector`() {
        assertEquals("1f527", EmojiResolver.codepointHex("🔧"))
        assertEquals("267b", EmojiResolver.codepointHex("♻️")) // U+267B U+FE0F -> 267b
        assertEquals("2728", EmojiResolver.codepointHex("✨"))
    }

    @Test
    fun `bundled svgs resolve for all default prefixes`() {
        for ((prefix, emoji) in EmojiResolver.DEFAULT_EMOJI_BY_PREFIX) {
            assertNotNull(EmojiResolver.svgBytesFor(emoji), "missing bundled SVG for $prefix -> $emoji")
        }
        assertNotNull(EmojiResolver.svgBytesFor(EmojiResolver.DEFAULT_EMOJI), "missing default emoji SVG")
    }

    @Test
    fun `bundled svg content looks like svg`() {
        val bytes = EmojiResolver.svgBytesFor("🔧")!!
        assertTrue(String(bytes).contains("<svg"))
    }

    @Test
    fun `unknown emoji returns null`() {
        assertNull(EmojiResolver.svgBytesFor("🦄"))
    }

    @Test
    fun `emojiFor prefers configured over defaults`() {
        val map = mapOf("fix" to "🌟")
        assertEquals("🌟", EmojiResolver.emojiFor("fix", map, "🌿"))
        assertEquals("🐛", EmojiResolver.emojiFor("bug", map, "🌿")) // falls back to built-in default
        assertEquals("🌿", EmojiResolver.emojiFor("unknownprefix", map, "🌿"))
    }

    @Test
    fun `emojiFor is case-insensitive`() {
        assertEquals("🔧", EmojiResolver.emojiFor("FIX", emptyMap(), "🌿"))
    }
}
