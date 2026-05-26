package com.rohittp.plugables.autoassert

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

class AutoAssertPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create("autoAssert", AutoAssertExtension::class.java)

        val generateAnnotations = project.tasks.register(
            "generateAutoAssertAnnotations",
            GenerateAutoAssertAnnotationsTask::class.java,
            Action {
                outputDir.set(ext.outputDir)
            },
        )

        project.tasks.matching { t -> t.name.matches(Regex("compile.*Kotlin")) }
            .configureEach(Action { dependsOn(generateAnnotations) })

        project.plugins.withId("com.android.application") {
            wireAndroid(project, generateAnnotations)
        }
        project.plugins.withId("com.android.library") {
            wireAndroid(project, generateAnnotations)
        }
    }

    private fun wireAndroid(
        project: Project,
        generateAnnotations: TaskProvider<GenerateAutoAssertAnnotationsTask>,
    ) {
        val androidComponents = project.extensions.getByType(
            AndroidComponentsExtension::class.java,
        )
        androidComponents.onVariants { variant ->
            variant.sources.kotlin?.addGeneratedSourceDirectory(
                generateAnnotations,
                GenerateAutoAssertAnnotationsTask::outputDir,
            )
            variant.sources.java?.addGeneratedSourceDirectory(
                generateAnnotations,
                GenerateAutoAssertAnnotationsTask::outputDir,
            )
            variant.instrumentation.transformClassesWith(
                AssertInjectorFactory::class.java,
                InstrumentationScope.ALL,
            ) { }
            variant.instrumentation.setAsmFramesComputationMode(
                FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS,
            )
        }
    }
}
