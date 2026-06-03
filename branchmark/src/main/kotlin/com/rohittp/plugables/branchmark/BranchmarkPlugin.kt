package com.rohittp.plugables.branchmark

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Stamps the debug launcher icon with the current git branch.
 *
 * Reads the app's existing adaptive launcher icon from `src/main/res`, generates a banner-stamped
 * foreground per density plus a debug adaptive XML, and wires the generated `res` into the matching
 * build type's variant sources via the AGP Variant API. Only activates when the Android application
 * plugin is present.
 */
class BranchmarkPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create("branchmark", BranchmarkExtension::class.java)
        val branch = BranchResolver.resolve(project, ext)
        val mainRes = project.layout.projectDirectory.dir("src/main/res")
        val outDir = project.layout.buildDirectory.dir("generated/branchmark/res")

        val task = project.tasks.register(
            "generateBranchmarkIcon",
            GenerateBranchmarkIconTask::class.java,
            Action {
                this.branch.set(branch)
                launcherIconName.set(ext.launcherIconName)
                foregroundResourceName.set(ext.foregroundResourceName)
                ribbonColor.set(ext.ribbonColor)
                ribbonTextColor.set(ext.ribbonTextColor)
                fallbackRibbonText.set(ext.fallbackRibbonText)
                defaultEmoji.set(ext.defaultEmoji)
                densities.set(ext.densities)
                emojiByPrefix.set(ext.emojiByPrefix)
                mainResDir.set(mainRes)
                iconInputs.from(
                    project.fileTree(mainRes).apply { include("mipmap-*/**", "drawable*/**") }
                )
                outputDir.set(outDir)
            }
        )

        project.plugins.withId("com.android.application") {
            val acx = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            acx.onVariants { variant ->
                if (variant.buildType == ext.buildType.get()) {
                    variant.sources.res?.addGeneratedSourceDirectory(
                        task, GenerateBranchmarkIconTask::outputDir
                    )
                }
            }
        }
    }
}
