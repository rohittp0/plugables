package com.rohittp.plugables.branchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BranchInfoTest {

    @Test
    fun `type slash name is detectable`() {
        val info = BranchInfo.parse("fix/login-crash")
        assertFalse(info.undetectable)
        assertEquals("fix", info.prefix)
        assertEquals("login-crash", info.suffix)
        assertEquals("login-crash", info.displaySuffix)
    }

    @Test
    fun `only the final two segments are used`() {
        val info = BranchInfo.parse("john/fix/login-crash")
        assertEquals("fix", info.prefix)
        assertEquals("login-crash", info.suffix)
        assertEquals("login-crash", info.displaySuffix)
    }

    @Test
    fun `deeply nested branch uses the last two segments`() {
        val info = BranchInfo.parse("team/john/feat/new-onboarding")
        assertEquals("feat", info.prefix)
        assertEquals("new-onboarding", info.displaySuffix)
    }

    @Test
    fun `slashless branch is undetectable`() {
        val info = BranchInfo.parse("main")
        assertTrue(info.undetectable)
        assertNull(info.prefix)
    }

    @Test
    fun `detached HEAD is undetectable`() {
        assertTrue(BranchInfo.parse("HEAD").undetectable)
    }

    @Test
    fun `blank is undetectable`() {
        assertTrue(BranchInfo.parse("").undetectable)
        assertTrue(BranchInfo.parse("   ").undetectable)
    }

    @Test
    fun `empty suffix after slash is undetectable`() {
        assertTrue(BranchInfo.parse("fix/").undetectable)
    }

    @Test
    fun `whitespace is trimmed`() {
        val info = BranchInfo.parse("  fix/bug  ")
        assertEquals("fix", info.prefix)
        assertEquals("bug", info.displaySuffix)
    }
}
