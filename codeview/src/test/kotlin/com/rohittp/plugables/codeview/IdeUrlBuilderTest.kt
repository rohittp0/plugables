package com.rohittp.plugables.codeview

import kotlin.test.Test
import kotlin.test.assertEquals

class IdeUrlBuilderTest {

    @Test
    fun `idea scheme produces correct URL`() {
        assertEquals(
            "idea://open?file=/Users/r/Home.kt&line=42",
            IdeUrlBuilder.build("idea", "/Users/r/Home.kt", 42),
        )
    }

    @Test
    fun `paths with spaces are percent-encoded`() {
        assertEquals(
            "idea://open?file=/Users/r%20o/Home%20Screen.kt&line=1",
            IdeUrlBuilder.build("idea", "/Users/r o/Home Screen.kt", 1),
        )
    }

    @Test
    fun `vscode scheme uses path-style URL`() {
        assertEquals(
            "vscode://file/Users/r/H.kt:10",
            IdeUrlBuilder.build("vscode", "/Users/r/H.kt", 10),
        )
    }

    @Test
    fun `unknown scheme falls back to idea`() {
        assertEquals(
            "idea://open?file=/x/y.kt&line=5",
            IdeUrlBuilder.build("emacs", "/x/y.kt", 5),
        )
    }
}
