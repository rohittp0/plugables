package com.rohittp.plugables.codeview

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(
    because = "Pulls files from a connected device; output depends on device state, not declared inputs."
)
abstract class PullCodeviewSidecarsTask @Inject constructor(
    private val exec: ExecOperations,
) : DefaultTask() {

    init {
        // Output depends on device state, not declared inputs. Always re-pull so any code
        // change that's already been re-tested produces a fresh report.
        outputs.upToDateWhen { false }
    }

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
        val deviceArgs = pickDevice(adb)

        logger.lifecycle("[codeview] Pulling sidecars from device path '$devicePath/.' to '${out.absolutePath}/'")
        exec.exec {
            commandLine(listOf(adb) + deviceArgs + listOf("pull", "$devicePath/.", out.absolutePath))
        }
        exec.exec {
            commandLine(listOf(adb) + deviceArgs + listOf("shell", "rm", "-rf", devicePath))
        }
    }

    /**
     * If multiple devices are attached, pick the first emulator (matches AGP's
     * `connected*AndroidTest` default selection). Returns the args to pass to adb
     * (`-s <serial>` or empty when only one device is present).
     */
    private fun pickDevice(adb: String): List<String> {
        val out = java.io.ByteArrayOutputStream()
        exec.exec {
            commandLine(adb, "devices")
            standardOutput = out
        }
        val devices = out.toString().lineSequence()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("*") }
            .mapNotNull {
                val parts = it.split("\\s+".toRegex())
                if (parts.size >= 2 && parts[1] == "device") parts[0] else null
            }
            .toList()
        return when {
            devices.size <= 1 -> emptyList()
            else -> {
                val pick = devices.firstOrNull { it.startsWith("emulator-") } ?: devices.first()
                listOf("-s", pick)
            }
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
