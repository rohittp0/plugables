package com.rohittp.plugables.branchmark

/**
 * Parsed view of a git branch name, used to decide what the banner shows.
 *
 * Only the **final two** `/`-separated segments matter, matching `.../<type>/<name>` branch
 * conventions (e.g. `john/fix/login-crash`, `fix/login-crash`):
 * - A branch with at least two segments is **detectable**: the second-to-last segment is the
 *   **type/prefix** (chooses the emoji) and the last segment is the **name** (the ribbon text).
 *   Any leading segments are ignored.
 * - A slashless branch (`main`), a detached `HEAD`, or an empty branch is **undetectable**: the ribbon
 *   shows the static fallback text ("DEBUG") and no emoji is drawn.
 */
data class BranchInfo(
    val raw: String,
    /** Branch type — the second-to-last `/`-segment (`fix` for `john/fix/login`). */
    val prefix: String?,
    /** Branch name — the last `/`-segment (`login` for `john/fix/login`). */
    val suffix: String?,
    /** Ribbon text; identical to [suffix] (the last segment). Kept separate for renderer clarity. */
    val displaySuffix: String?,
    val undetectable: Boolean,
) {
    companion object {
        /**
         * Parse a raw branch string. Blank, `"HEAD"` (detached), or fewer than two `/`-segments are
         * undetectable. Otherwise the type/prefix and name come from the final two segments.
         */
        fun parse(raw: String): BranchInfo {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed == "HEAD") {
                return BranchInfo(trimmed, null, null, null, undetectable = true)
            }
            val segments = trimmed.split('/').filter { it.isNotEmpty() }
            if (segments.size < 2) {
                return BranchInfo(trimmed, null, null, null, undetectable = true)
            }
            val prefix = segments[segments.size - 2]
            val name = segments.last()
            return BranchInfo(trimmed, prefix, name, name, undetectable = false)
        }
    }
}
