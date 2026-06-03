package com.rohittp.plugables.branchmark

import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * Builds the branch-name [Provider] from, in order of precedence:
 * 1. `branchmark.branchOverride`
 * 2. the `gitBranch` Gradle property
 * 3. the `GITHUB_HEAD_REF` environment variable (GitHub Actions PR builds)
 * 4. the `GIT_BRANCH` environment variable (common CI convention)
 * 5. `git rev-parse --abbrev-ref HEAD` via [GitBranchValueSource]
 *
 * The chain stays lazy and configuration-cache safe. The resulting raw string is parsed into a
 * [BranchInfo] at task execution time.
 */
object BranchResolver {
    fun resolve(project: Project, ext: BranchmarkExtension): Provider<String> =
        ext.branchOverride
            .orElse(project.providers.gradleProperty("gitBranch"))
            .orElse(project.providers.environmentVariable("GITHUB_HEAD_REF"))
            .orElse(project.providers.environmentVariable("GIT_BRANCH"))
            .orElse(project.providers.of(GitBranchValueSource::class.java) {})
}
