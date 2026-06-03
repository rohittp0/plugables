package com.rohittp.plugables.branchmark

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * Functional tests via TestKit. The plugin registers `generateBranchmarkIcon` unconditionally (only
 * the AGP wiring is gated on the Android plugin), so we can drive the real task without AGP.
 */
class BranchmarkTestKitTest {

    private fun scaffold(tmp: File) {
        File(tmp, "settings.gradle.kts").writeText("""rootProject.name = "fixture"""")
        File(tmp, "build.gradle.kts").writeText(
            """
            plugins { id("com.rohittp.plugables.branchmark") }
            branchmark { densities.set(listOf("xxxhdpi")) }
            """.trimIndent()
        )
        val res = File(tmp, "src/main/res").apply { mkdirs() }
        IconFixtures.writeAdaptiveIconSet(res)
    }

    private fun runner(tmp: File, vararg args: String) =
        GradleRunner.create()
            .withProjectDir(tmp)
            .withPluginClasspath()
            .withArguments(*args)

    @Test
    fun `task runs then is up-to-date, re-executes on branch change`(@TempDir tmp: File) {
        scaffold(tmp)

        val first = runner(tmp, "generateBranchmarkIcon", "-PgitBranch=fix/bug").build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":generateBranchmarkIcon")!!.outcome)
        assertTrue(File(tmp, "build/generated/branchmark/res/mipmap-xxxhdpi/ic_launcher_foreground_debug.png").isFile)

        val second = runner(tmp, "generateBranchmarkIcon", "-PgitBranch=fix/bug").build()
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateBranchmarkIcon")!!.outcome)

        val changed = runner(tmp, "generateBranchmarkIcon", "-PgitBranch=main").build()
        assertEquals(TaskOutcome.SUCCESS, changed.task(":generateBranchmarkIcon")!!.outcome)
    }

    @Test
    fun `configuration cache is reused on second run`(@TempDir tmp: File) {
        scaffold(tmp)

        runner(tmp, "generateBranchmarkIcon", "-PgitBranch=fix/bug", "--configuration-cache").build()
        val second = runner(tmp, "generateBranchmarkIcon", "-PgitBranch=fix/bug", "--configuration-cache").build()
        assertTrue(
            second.output.contains("Reusing configuration cache"),
            "expected configuration cache reuse, output was:\n${second.output}",
        )
    }
}
