package com.rohittp.plugables.codeview

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

abstract class PullCodeviewSidecarsTask @Inject constructor(
    private val exec: ExecOperations,
) : DefaultTask() {

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val onDeviceSubdir: Property<String>

    @get:Input
    @get:Optional
    abstract val sdkDir: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun pull() {
        val out = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val devicePath = "/sdcard/codeview-sidecars"
        val adb = resolveAdb()

        logger.lifecycle("[codeview] Pulling sidecars from device path '$devicePath/.' to '${out.absolutePath}/'")
        exec.exec {
            commandLine(adb, "pull", "$devicePath/.", out.absolutePath)
        }
        exec.exec {
            commandLine(adb, "shell", "rm", "-rf", devicePath)
        }
    }

    private fun resolveAdb(): String {
        val sdk = sdkDir.orNull
            ?: System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
        if (sdk != null) {
            val candidate = File(sdk, "platform-tools/adb")
            if (candidate.exists()) return candidate.absolutePath
        }
        return "adb"
    }
}
