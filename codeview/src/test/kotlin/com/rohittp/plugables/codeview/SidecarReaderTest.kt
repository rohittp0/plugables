package com.rohittp.plugables.codeview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SidecarReaderTest {

    @Test
    fun `parses a v3 sidecar with sourceHash and renderError`() {
        val json = """
            {"schemaVersion":3,"id":"X","previewFqn":"a.b","displayName":"X","sourceFile":"f.kt",
             "sourceLine":1,"sourceHash":"abc","imageWidth":100,"imageHeight":200,
             "renderError":"boom: it failed",
             "renderedTexts":["hello","world"],
             "nodes":[]}
        """.trimIndent()
        val parsed = SidecarReader.read(json)
        assertEquals(3, parsed.schemaVersion)
        assertEquals("abc", parsed.sourceHash)
        assertEquals("boom: it failed", parsed.renderError)
        assertEquals(100, parsed.imageWidth)
        assertEquals(200, parsed.imageHeight)
        assertEquals(listOf("hello", "world"), parsed.renderedTexts)
        assertEquals(emptyList(), parsed.nodes)
    }

    @Test
    fun `parses nodes with bounds and optional sourceFileName and parentId`() {
        val json = """
            {"schemaVersion":3,"id":"X","sourceHash":"h","imageWidth":0,"imageHeight":0,
             "renderedTexts":[],
             "nodes":[
               {"id":1,"name":"Group","bounds":{"x":0,"y":0,"width":10,"height":20},
                "sourceFileName":null,"packageHash":0,"line":-1,"parentId":null},
               {"id":2,"name":"Text","bounds":{"x":5,"y":5,"width":50,"height":12},
                "sourceFileName":"Foo.kt","packageHash":42,"line":7,"parentId":1}
             ]}
        """.trimIndent()
        val parsed = SidecarReader.read(json)
        assertEquals(2, parsed.nodes.size)
        assertEquals(null, parsed.nodes[0].sourceFileName)
        assertEquals(null, parsed.nodes[0].parentId)
        assertEquals("Foo.kt", parsed.nodes[1].sourceFileName)
        assertEquals(1, parsed.nodes[1].parentId)
    }

    @Test
    fun `does not stack-overflow on a multi-KB renderError string`() {
        // Regression: the old regex-based parser blew the JVM stack on long string fields
        // because Java's regex `(?:[^"\\]|\\.)*` matcher recurses one frame per character.
        // 100 KB is way beyond the previous threshold.
        val longError = "x".repeat(100_000)
        val json = """{"schemaVersion":3,"sourceHash":"h","imageWidth":0,"imageHeight":0,"renderError":"$longError","renderedTexts":[],"nodes":[]}"""
        val parsed = SidecarReader.read(json)
        assertEquals(longError, parsed.renderError)
    }

    @Test
    fun `does not stack-overflow on a long renderedTexts array`() {
        val many = (1..2000).joinToString(",") { "\"text-$it\"" }
        val json = """{"schemaVersion":3,"sourceHash":"h","imageWidth":0,"imageHeight":0,"renderedTexts":[$many],"nodes":[]}"""
        val parsed = SidecarReader.read(json)
        assertEquals(2000, parsed.renderedTexts.size)
        assertEquals("text-1", parsed.renderedTexts.first())
        assertEquals("text-2000", parsed.renderedTexts.last())
    }

    @Test
    fun `string escapes are preserved`() {
        val json = """{"schemaVersion":3,"sourceHash":"h","imageWidth":0,"imageHeight":0,"renderError":"line1\nline2\t\"q\"","renderedTexts":[],"nodes":[]}"""
        val parsed = SidecarReader.read(json)
        assertEquals("line1\nline2\t\"q\"", parsed.renderError)
    }

    @Test
    fun `v1 sidecar without sourceHash returns null hash`() {
        val json = """{"schemaVersion":1,"imageWidth":0,"imageHeight":0,"renderedTexts":[],"nodes":[]}"""
        val parsed = SidecarReader.read(json)
        assertEquals(1, parsed.schemaVersion)
        assertNull(parsed.sourceHash)
        assertNull(parsed.renderError)
    }

    @Test
    fun `malformed JSON throws (caller is expected to runCatching)`() {
        val result = runCatching { SidecarReader.read("not json") }
        assertTrue(result.isFailure, "malformed input should throw")
    }
}
