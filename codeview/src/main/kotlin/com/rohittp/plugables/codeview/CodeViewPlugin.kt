package com.rohittp.plugables.codeview

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HasDeviceTests
import com.android.build.api.variant.HasHostTests
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

class CodeViewPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create("codeview", CodeViewExtension::class.java)

        val testsDir = project.layout.buildDirectory.dir("generated/source/codeview/test")
        val sidecarsDir = project.layout.buildDirectory.dir("generated/codeview/sidecars")
        val indexFile = project.layout.buildDirectory.file("generated/codeview/preview-index.json")
        val onDeviceSubdir = "codeview"

        val generateTests = project.tasks.register(
            "generateCodeviewPreviewTests",
            GeneratePreviewTestsTask::class.java,
            Action {
                sourceDirs.from(ext.sourceDirs)
                testsOutputDir.set(testsDir)
                sidecarOutputDir.set(sidecarsDir)
                indexOutputFile.set(indexFile)
                testActivityClass.set(ext.testActivityClass)
                testMode.set(ext.testMode)
                onDeviceSidecarSubdir.set(onDeviceSubdir)
            }
        )

        val aggregateReport = project.tasks.register("codeviewReport", CodeviewReportTask::class.java, Action {
            previewIndexFile.set(indexFile)
            this.sidecarsDir.set(sidecarsDir)
            sourceDirs.from(ext.sourceDirs)
            outputDir.set(ext.outputDir)
            ideScheme.set(ext.ideScheme)
            openOnComplete.set(ext.openOnComplete)
            dependsOn(generateTests)
        })

        project.plugins.withId("com.android.application") {
            val acx = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            acx.onVariants { variant ->
                wireGeneratedSourcesFor(variant, ext, generateTests)
                registerPerVariantReport(project, ext, indexFile, sidecarsDir, generateTests, aggregateReport, variant, onDeviceSubdir)
            }
        }
        project.plugins.withId("com.android.library") {
            val acx = project.extensions.getByType(LibraryAndroidComponentsExtension::class.java)
            acx.onVariants { variant ->
                wireGeneratedSourcesFor(variant, ext, generateTests)
                registerPerVariantReport(project, ext, indexFile, sidecarsDir, generateTests, aggregateReport, variant, onDeviceSubdir)
            }
        }
    }

    private fun wireGeneratedSourcesFor(
        variant: Variant,
        ext: CodeViewExtension,
        generateTests: TaskProvider<GeneratePreviewTestsTask>,
    ) {
        when (ext.testMode.get()) {
            "instrumented" -> {
                if (variant !is HasDeviceTests) return
                variant.deviceTests.values.forEach { deviceTest ->
                    deviceTest.sources.kotlin?.addGeneratedSourceDirectory(
                        generateTests, GeneratePreviewTestsTask::testsOutputDir
                    )
                }
            }
            else -> {
                if (variant !is HasHostTests) return
                variant.hostTests.values.forEach { hostTest ->
                    hostTest.sources.kotlin?.addGeneratedSourceDirectory(
                        generateTests, GeneratePreviewTestsTask::testsOutputDir
                    )
                }
            }
        }
    }

    private fun registerPerVariantReport(
        project: Project,
        ext: CodeViewExtension,
        indexFile: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
        sidecarsDirProvider: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
        generateTests: TaskProvider<GeneratePreviewTestsTask>,
        aggregate: TaskProvider<CodeviewReportTask>,
        variant: Variant,
        onDeviceSubdir: String,
    ) {
        val cap = variant.name.replaceFirstChar { it.uppercaseChar() }
        val mode = ext.testMode.get()

        val (testTaskName, hasTests) = when (mode) {
            "instrumented" -> "connected${cap}AndroidTest" to ((variant as? HasDeviceTests)?.deviceTests?.isNotEmpty() ?: false)
            else -> "test${cap}UnitTest" to ((variant as? HasHostTests)?.hostTests?.isNotEmpty() ?: false)
        }
        if (!hasTests) return

        val pullSidecars = if (mode == "instrumented") {
            val appId = (variant as? com.android.build.api.variant.ApplicationVariant)?.applicationId
            val subdirCapture = onDeviceSubdir
            val sdkDirValue = readSdkDir(project)
            project.tasks.register("pullCodeviewSidecars$cap", PullCodeviewSidecarsTask::class.java, Action {
                if (appId != null) applicationId.set(appId)
                this.onDeviceSubdir.set(subdirCapture)
                if (sdkDirValue != null) sdkDir.set(sdkDirValue)
                outputDir.set(sidecarsDirProvider)
                dependsOn(project.tasks.named(testTaskName))
            })
        } else null

        val perVariant = project.tasks.register(
            "codeviewReport$cap",
            CodeviewReportTask::class.java,
            Action {
                previewIndexFile.set(indexFile)
                sidecarsDir.set(sidecarsDirProvider)
                sourceDirs.from(ext.sourceDirs)
                outputDir.set(ext.outputDir.map { it.dir(variant.name) })
                ideScheme.set(ext.ideScheme)
                openOnComplete.set(ext.openOnComplete)
                dependsOn(generateTests)
                if (pullSidecars != null) {
                    dependsOn(pullSidecars)
                } else {
                    dependsOn(project.tasks.named(testTaskName))
                }
            }
        )

        aggregate.configure { dependsOn(perVariant) }
    }

    private fun readSdkDir(project: Project): String? {
        val local = project.rootProject.file("local.properties")
        if (!local.exists()) return null
        return local.readLines()
            .firstOrNull { it.startsWith("sdk.dir=") }
            ?.substringAfter("=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
