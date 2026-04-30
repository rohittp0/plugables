package com.rohittp.plugables.codeview

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GeneratePreviewTestsTask : DefaultTask() {

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val testsOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val sidecarOutputDir: DirectoryProperty

    @get:OutputFile
    abstract val indexOutputFile: RegularFileProperty

    @get:Input
    abstract val testActivityClass: Property<String>

    @get:Input
    abstract val testMode: Property<String>

    /** On-device sidecar subdirectory used by instrumented tests (under context.externalCacheDir). */
    @get:Input
    abstract val onDeviceSidecarSubdir: Property<String>

    /**
     * Sidecars from the previous run, read at execution time to decide which previews can be
     * `@Ignore`d. Marked `@Internal` (not a Gradle input) on purpose: the directory is also the
     * output of `pullCodeviewSidecars`, so declaring it as `@InputDirectory` here would create a
     * cycle (`generate → pull → connectedAndroidTest → generate`). The trade-off: Gradle won't
     * re-run this task when sidecars change unrelated to sources — `--rerun-tasks` is the escape.
     */
    @get:Internal
    abstract val previousSidecarsDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val testsDir = testsOutputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val sidecarDir = sidecarOutputDir.get().asFile.apply { mkdirs() }
        val indexFile = indexOutputFile.get().asFile.apply { parentFile.mkdirs() }

        val parseResults = sourceDirs.files.asSequence()
            .filter { it.exists() }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }
            .map { PreviewSourceParser.parseFile(it) }
            .toList()
        val previews = parseResults.flatMap { it.specs }
        val skippedPrivate = parseResults.flatMap { it.skippedPrivate }
        if (skippedPrivate.isNotEmpty()) {
            logger.warn(
                "[codeview] Skipped ${skippedPrivate.size} private @Preview function(s) — " +
                    "top-level `private fun` is file-scoped in Kotlin and cannot be invoked " +
                    "from generated tests. Make them `internal` or non-private to include them:\n  - " +
                    skippedPrivate.joinToString("\n  - ")
            )
        }

        val pkgDir = File(testsDir, "com/rohittp/plugables/codeview/generated").apply { mkdirs() }
        File(pkgDir, "CodeviewRuntime.kt").writeText(loadHelper())

        val previousDir = previousSidecarsDir.orNull?.asFile
        val skipIds = computeSkipIds(previews, previousDir)

        val activityFqn = testActivityClass.get()
        val mode = testMode.get()
        previews.forEach { spec ->
            val skipRender = spec.id in skipIds
            val source = when (mode) {
                "instrumented" -> renderInstrumentedTestClass(spec, onDeviceSidecarSubdir.get(), activityFqn, skipRender)
                else -> renderUnitTestClass(spec, sidecarDir.absolutePath, activityFqn, skipRender)
            }
            File(pkgDir, "${spec.id}Test.kt").writeText(source)
        }

        indexFile.writeText(renderIndex(previews))
        val rendered = previews.size - skipIds.size
        if (skipIds.isEmpty()) {
            logger.lifecycle("[codeview] Generated ${previews.size} preview test class(es) in '$mode' mode.")
        } else {
            logger.lifecycle(
                "[codeview] Generated ${previews.size} preview test class(es) in '$mode' mode " +
                    "($rendered to render, ${skipIds.size} unchanged → @Ignore)."
            )
        }
    }

    private fun computeSkipIds(previews: List<PreviewSpec>, previousDir: File?): Set<String> {
        if (previousDir == null || !previousDir.isDirectory) return emptySet()
        val skip = mutableSetOf<String>()
        for (spec in previews) {
            val sidecarFile = File(previousDir, "${spec.id}.json")
            if (!sidecarFile.isFile) continue
            val parsed = runCatching { SidecarReader.read(sidecarFile.readText()) }.getOrNull() ?: continue
            // Schema must be v2+ (older sidecars don't carry sourceHash) and the hash must match.
            if (parsed.schemaVersion >= 2 && parsed.sourceHash == spec.sourceHash) {
                skip.add(spec.id)
            }
        }
        return skip
    }

    private fun loadHelper(): String =
        GeneratePreviewTestsTask::class.java.getResourceAsStream("/codeview-runtime-helpers.kt.tpl")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Missing /codeview-runtime-helpers.kt.tpl on classpath")

    private fun renderUnitTestClass(spec: PreviewSpec, sidecarDir: String, activityFqn: String, skipRender: Boolean): String {
        val safeSidecarDir = sidecarDir.replace("\\", "\\\\")
        val safeSourceFile = (spec.source.file ?: "").replace("\\", "\\\\")
        return buildString {
            appendLine("// AUTO-GENERATED by codeview. Do not edit.")
            appendLine("@file:Suppress(\"unused\")")
            appendLine("@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)")
            appendLine("package com.rohittp.plugables.codeview.generated")
            appendLine()
            appendLine("import androidx.compose.ui.test.runAndroidComposeUiTest")
            appendLine("import androidx.test.ext.junit.runners.AndroidJUnit4")
            if (skipRender) appendLine("import org.junit.Ignore")
            appendLine("import org.junit.Test")
            appendLine("import org.junit.runner.RunWith")
            appendLine("import org.robolectric.annotation.Config")
            appendLine("import org.robolectric.annotation.GraphicsMode")
            appendLine("import java.io.File")
            appendLine()
            appendLine("@RunWith(AndroidJUnit4::class)")
            appendLine("@Config(sdk = [35])")
            appendLine("@GraphicsMode(GraphicsMode.Mode.NATIVE)")
            appendLine("class ${spec.id}Test {")
            appendLine()
            if (skipRender) appendLine("    @Ignore(\"codeview: source unchanged since last render\")")
            appendLine("    @Test")
            appendLine("    fun render() = runAndroidComposeUiTest(activityClass = $activityFqn::class.java) {")
            appendLine("        CodeviewRuntime.renderAndCapture(")
            appendLine("            uiTest = this,")
            appendLine("            outputDir = File(\"$safeSidecarDir\"),")
            appendLine("            previewId = \"${spec.id}\",")
            appendLine("            previewFqn = \"${spec.previewFqn}\",")
            appendLine("            previewDisplayName = \"${spec.displayName}\",")
            appendLine("            previewSourceFile = \"$safeSourceFile\",")
            appendLine("            previewSourceLine = ${spec.source.line},")
            appendLine("            previewSourceHash = \"${spec.sourceHash}\",")
            appendLine("        ) { ${spec.previewFqn}() }")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun renderInstrumentedTestClass(spec: PreviewSpec, onDeviceSubdir: String, activityFqn: String, skipRender: Boolean): String {
        val safeSourceFile = (spec.source.file ?: "").replace("\\", "\\\\")
        return buildString {
            appendLine("// AUTO-GENERATED by codeview. Do not edit.")
            appendLine("@file:Suppress(\"unused\")")
            appendLine("@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)")
            appendLine("package com.rohittp.plugables.codeview.generated")
            appendLine()
            appendLine("import androidx.compose.ui.test.runAndroidComposeUiTest")
            appendLine("import androidx.test.ext.junit.runners.AndroidJUnit4")
            appendLine("import androidx.test.platform.app.InstrumentationRegistry")
            if (skipRender) appendLine("import org.junit.Ignore")
            appendLine("import org.junit.Test")
            appendLine("import org.junit.runner.RunWith")
            appendLine("import java.io.File")
            appendLine()
            appendLine("@RunWith(AndroidJUnit4::class)")
            appendLine("class ${spec.id}Test {")
            appendLine()
            if (skipRender) appendLine("    @Ignore(\"codeview: source unchanged since last render\")")
            appendLine("    @Test")
            appendLine("    fun render() = runAndroidComposeUiTest(activityClass = $activityFqn::class.java) {")
            appendLine("        val ctx = InstrumentationRegistry.getInstrumentation().targetContext")
            appendLine("        val outputDir = File(ctx.externalCacheDir, \"$onDeviceSubdir\").apply { mkdirs() }")
            appendLine("        File(outputDir, \"__codeview_published__\").writeText(\"1\")")
            appendLine("        CodeviewRuntime.renderAndCapture(")
            appendLine("            uiTest = this,")
            appendLine("            outputDir = outputDir,")
            appendLine("            previewId = \"${spec.id}\",")
            appendLine("            previewFqn = \"${spec.previewFqn}\",")
            appendLine("            previewDisplayName = \"${spec.displayName}\",")
            appendLine("            previewSourceFile = \"$safeSourceFile\",")
            appendLine("            previewSourceLine = ${spec.source.line},")
            appendLine("            previewSourceHash = \"${spec.sourceHash}\",")
            appendLine("        ) { ${spec.previewFqn}() }")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun renderIndex(previews: List<PreviewSpec>): String {
        val items = previews.joinToString(",") { spec ->
            buildString {
                append('{')
                append("\"id\":\"").append(spec.id).append("\",")
                append("\"previewFqn\":\"").append(spec.previewFqn).append("\",")
                append("\"displayName\":\"").append(spec.displayName).append("\",")
                append("\"sourceFile\":\"").append((spec.source.file ?: "").replace("\\", "\\\\")).append("\",")
                append("\"sourceLine\":").append(spec.source.line).append(',')
                append("\"sourceHash\":\"").append(spec.sourceHash).append("\"")
                append('}')
            }
        }
        return "{\"schemaVersion\":2,\"previews\":[$items]}"
    }
}
