package com.rohittp.plugables.branchmark

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class GenerateBranchmarkIconTaskTest {

    private fun runForBranch(tmp: File, branch: String, name: String): File {
        val project = ProjectBuilder.builder().withProjectDir(tmp).build()
        val res = File(tmp, "src/main/res").apply { mkdirs() }
        IconFixtures.writeAdaptiveIconSet(res)
        val out = File(tmp, "out-$name")

        val task = project.tasks.register("gen$name", GenerateBranchmarkIconTask::class.java) {
            this.branch.set(branch)
            launcherIconName.set("ic_launcher")
            foregroundResourceName.set("ic_launcher_foreground_debug")
            ribbonColor.set("#D32F2F")
            ribbonTextColor.set("#FFFFFF")
            fallbackRibbonText.set("DEBUG")
            defaultEmoji.set("🌿")
            densities.set(listOf("xxxhdpi"))
            emojiByPrefix.set(EmojiResolver.DEFAULT_EMOJI_BY_PREFIX)
            mainResDir.set(res)
            outputDir.set(out)
        }.get()

        task.generate()
        return out
    }

    @Test
    fun `produces stamped foreground and debug adaptive xml`(@TempDir tmp: File) {
        val out = runForBranch(tmp, "fix/bug", "Fix")

        val png = File(out, "mipmap-xxxhdpi/ic_launcher_foreground_debug.png")
        assertTrue(png.isFile && png.length() > 0, "stamped foreground PNG missing")

        val xml = File(out, "mipmap-anydpi-v26/ic_launcher.xml").readText()
        assertTrue(xml.contains("""@mipmap/ic_launcher_foreground_debug"""))
        assertTrue(xml.contains("""@drawable/ic_launcher_background"""))

        // round XML generated because the fixture has one
        assertTrue(File(out, "mipmap-anydpi-v26/ic_launcher_round.xml").isFile)
    }

    @Test
    fun `dynamic branch differs from fallback branch`(@TempDir tmp: File) {
        val fix = File(runForBranch(File(tmp, "a").apply { mkdirs() }, "fix/bug", "Fix"), "mipmap-xxxhdpi/ic_launcher_foreground_debug.png")
        val main = File(runForBranch(File(tmp, "b").apply { mkdirs() }, "main", "Main"), "mipmap-xxxhdpi/ic_launcher_foreground_debug.png")

        assertFalse(fix.readBytes().contentEquals(main.readBytes()), "fix/bug and main icons should differ")
    }
}
