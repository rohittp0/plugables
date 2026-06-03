package com.rohittp.plugables.branchmark

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Configuration-cache-safe git branch lookup. Running an external process through a [ValueSource]
 * (rather than at configuration time) lets Gradle cache the result and only re-run when it changes.
 * Any failure (no git, detached HEAD, not a repo) is swallowed and reported as an empty string.
 */
abstract class GitBranchValueSource @Inject constructor(
    private val exec: ExecOperations,
) : ValueSource<String, ValueSourceParameters.None> {

    override fun obtain(): String = runCatching {
        val out = ByteArrayOutputStream()
        exec.exec {
            commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
            standardOutput = out
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        out.toString().trim()
    }.getOrDefault("")
}
