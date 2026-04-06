package com.rohittp.plugables.viewmodelstub

import com.android.build.gradle.BaseExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

class ViewModelStubPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create("viewModelStub", ViewModelStubExtension::class.java)

        val generateTask = project.tasks.register(
            "generateViewModelStubs",
            GenerateViewModelStubsTask::class.java,
            Action {
                sourceDir.set(ext.sourceDir)
                outputDir.set(ext.outputDir)
            }
        )

        project.afterEvaluate {
            // Wire output dir into Android source set so generated files are compiled
            // Use .get().asFile inside afterEvaluate — the value is available by now
            val android = project.extensions.findByName("android") as? BaseExtension
            android?.sourceSets?.getByName("main")?.java?.srcDir(ext.outputDir.get().asFile)

            // Ensure generation runs before any Kotlin compilation task
            project.tasks.matching { t -> t.name.matches(Regex("compile.*Kotlin")) }
                .configureEach(Action { dependsOn(generateTask) })
        }
    }
}
