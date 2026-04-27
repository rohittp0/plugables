package com.rohittp.plugables.codeview

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
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

        val generateTests = project.tasks.register(
            "generateCodeviewPreviewTests",
            GeneratePreviewTestsTask::class.java,
            Action {
                sourceDirs.from(ext.sourceDirs)
                testsOutputDir.set(testsDir)
                sidecarOutputDir.set(sidecarsDir)
                indexOutputFile.set(indexFile)
            }
        )

        val aggregateReport = project.tasks.register("codeviewReport", CodeviewReportTask::class.java, Action {
            previewIndexFile.set(indexFile)
            this.sidecarsDir.set(sidecarsDir)
            sourceDirs.from(ext.sourceDirs)
            outputDir.set(ext.outputDir)
            ideScheme.set(ext.ideScheme)
            dependsOn(generateTests)
        })

        project.plugins.withId("com.android.application") {
            val acx = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            acx.onVariants { variant ->
                wireUnitTestSourcesFor(variant, generateTests)
                registerPerVariantReport(project, ext, indexFile, sidecarsDir, generateTests, aggregateReport, variant)
            }
        }
        project.plugins.withId("com.android.library") {
            val acx = project.extensions.getByType(LibraryAndroidComponentsExtension::class.java)
            acx.onVariants { variant ->
                wireUnitTestSourcesFor(variant, generateTests)
                registerPerVariantReport(project, ext, indexFile, sidecarsDir, generateTests, aggregateReport, variant)
            }
        }
    }

    private fun wireUnitTestSourcesFor(
        variant: Variant,
        generateTests: TaskProvider<GeneratePreviewTestsTask>,
    ) {
        if (variant !is HasHostTests) return
        variant.hostTests.values.forEach { hostTest ->
            hostTest.sources.kotlin?.addGeneratedSourceDirectory(
                generateTests, GeneratePreviewTestsTask::testsOutputDir
            )
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
    ) {
        // Skip variants without host tests — AGP 9 disables release-variant unit tests by default.
        val hasUnitTests = (variant as? HasHostTests)?.hostTests?.isNotEmpty() ?: false
        if (!hasUnitTests) return

        val cap = variant.name.replaceFirstChar { it.uppercaseChar() }
        val testTaskName = "test${cap}UnitTest"

        val perVariant = project.tasks.register(
            "codeviewReport$cap",
            CodeviewReportTask::class.java,
            Action {
                previewIndexFile.set(indexFile)
                sidecarsDir.set(sidecarsDirProvider)
                sourceDirs.from(ext.sourceDirs)
                outputDir.set(ext.outputDir.map { it.dir(variant.name) })
                ideScheme.set(ext.ideScheme)
                dependsOn(generateTests)
                dependsOn(project.tasks.named(testTaskName))
            }
        )

        aggregate.configure { dependsOn(perVariant) }
    }
}
