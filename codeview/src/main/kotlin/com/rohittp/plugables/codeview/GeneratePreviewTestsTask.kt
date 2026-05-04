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
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(
    because = "Reads the previous run's pulled sidecars (`previousSidecarsDir`) at execution " +
        "time to decide which previews to mark @Ignore. That dir is also `pullCodeviewSidecars`'s " +
        "output, so declaring it as a cacheable input would create a producer-consumer cycle. " +
        "The task is cheap (a few small text writes) and is run on every invocation."
)
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

        File(pkgDir, "CodeviewPreviewRegistry.kt").writeText(renderRegistryFile(previews, skipIds))
        File(pkgDir, "CodeviewBatchTest.kt").writeText(
            renderBatchTestClass(mode, activityFqn, sidecarDir.absolutePath, onDeviceSidecarSubdir.get())
        )

        indexFile.writeText(renderIndex(previews))
        val rendered = previews.size - skipIds.size
        logger.lifecycle(
            "[codeview] Generated 1 batch test for ${previews.size} preview(s) in '$mode' mode " +
                "($rendered to render, ${skipIds.size} unchanged)."
        )
    }

    private fun computeSkipIds(previews: List<PreviewSpec>, previousDir: File?): Set<String> {
        if (previousDir == null || !previousDir.isDirectory) return emptySet()
        val skip = mutableSetOf<String>()
        for (spec in previews) {
            val sidecarFile = File(previousDir, "${spec.id}.json")
            if (!sidecarFile.isFile) continue
            val parsed = runCatching { SidecarReader.read(sidecarFile.readText()) }.getOrNull() ?: continue
            // Skip only if the previous run produced a *usable* sidecar:
            //   - schema is v2+ (carries sourceHash),
            //   - source hash still matches,
            //   - no recorded renderError,
            //   - bitmap actually captured (width/height > 0),
            //   - inspector tree captured (at least one node).
            // The last two are critical: silent capture failures produce a sidecar with
            // imageWidth=0 / nodes=[] and no renderError, which under the old rule got
            // permanently latched into SKIPPED_IDS even though the report had no usable
            // data for that preview.
            val usable = parsed.schemaVersion >= 2 &&
                parsed.sourceHash == spec.sourceHash &&
                parsed.renderError == null &&
                parsed.imageWidth > 0 && parsed.imageHeight > 0 &&
                parsed.nodes.isNotEmpty()
            if (usable) skip.add(spec.id)
        }
        return skip
    }

    private fun loadHelper(): String =
        GeneratePreviewTestsTask::class.java.getResourceAsStream("/codeview-runtime-helpers.kt.tpl")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Missing /codeview-runtime-helpers.kt.tpl on classpath")

    private fun renderRegistryFile(previews: List<PreviewSpec>, skipIds: Set<String>): String = buildString {
        appendLine("// AUTO-GENERATED by codeview. Do not edit.")
        appendLine("@file:Suppress(\"unused\")")
        appendLine("@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)")
        appendLine("package com.rohittp.plugables.codeview.generated")
        appendLine()
        appendLine("import androidx.compose.runtime.Composable")
        appendLine()
        appendLine("internal val PREVIEW_REGISTRY: List<Pair<CodeviewRuntime.PreviewMeta, @Composable () -> Unit>> = listOf(")
        for (spec in previews) {
            val safeSourceFile = (spec.source.file ?: "").replace("\\", "\\\\")
            appendLine("    CodeviewRuntime.PreviewMeta(")
            appendLine("        id = \"${spec.id}\",")
            appendLine("        fqn = \"${spec.previewFqn}\",")
            appendLine("        displayName = \"${spec.displayName}\",")
            appendLine("        sourceFile = \"$safeSourceFile\",")
            appendLine("        sourceLine = ${spec.source.line},")
            appendLine("        sourceHash = \"${spec.sourceHash}\",")
            appendLine("    ) to { ${spec.previewFqn}() },")
        }
        appendLine(")")
        appendLine()
        appendLine("internal val SKIPPED_IDS: Set<String> = setOf(")
        for (id in skipIds) appendLine("    \"$id\",")
        appendLine(")")
    }

    private fun renderBatchTestClass(
        mode: String,
        activityFqn: String,
        unitSidecarDir: String,
        onDeviceSubdir: String,
    ): String = when (mode) {
        "instrumented" -> renderInstrumentedBatchTest(activityFqn, onDeviceSubdir)
        else -> renderUnitBatchTest(activityFqn, unitSidecarDir)
    }

    private fun renderUnitBatchTest(activityFqn: String, sidecarDir: String): String {
        val safeSidecarDir = sidecarDir.replace("\\", "\\\\")
        return buildString {
            appendLine("// AUTO-GENERATED by codeview. Do not edit.")
            appendLine("@file:Suppress(\"unused\")")
            appendLine("@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)")
            appendLine("package com.rohittp.plugables.codeview.generated")
            appendLine()
            appendLine("import androidx.compose.ui.test.runAndroidComposeUiTest")
            appendLine("import androidx.test.ext.junit.runners.AndroidJUnit4")
            appendLine("import org.junit.Test")
            appendLine("import org.junit.runner.RunWith")
            appendLine("import org.robolectric.annotation.Config")
            appendLine("import org.robolectric.annotation.GraphicsMode")
            appendLine("import java.io.File")
            appendLine()
            appendLine("@RunWith(AndroidJUnit4::class)")
            appendLine("@Config(sdk = [35])")
            appendLine("@GraphicsMode(GraphicsMode.Mode.NATIVE)")
            appendLine("class CodeviewBatchTest {")
            appendLine()
            appendLine("    @Test")
            appendLine("    fun renderAll() = runAndroidComposeUiTest(activityClass = $activityFqn::class.java) {")
            appendLine("        CodeviewRuntime.runBatch(")
            appendLine("            uiTest = this,")
            appendLine("            outputDir = File(\"$safeSidecarDir\"),")
            appendLine("            registry = PREVIEW_REGISTRY,")
            appendLine("            skippedIds = SKIPPED_IDS,")
            appendLine("        )")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun renderInstrumentedBatchTest(activityFqn: String, onDeviceSubdir: String): String = buildString {
        appendLine("// AUTO-GENERATED by codeview. Do not edit.")
        appendLine("@file:Suppress(\"unused\")")
        appendLine("@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)")
        appendLine("package com.rohittp.plugables.codeview.generated")
        appendLine()
        appendLine("import androidx.compose.ui.test.runAndroidComposeUiTest")
        appendLine("import androidx.test.ext.junit.runners.AndroidJUnit4")
        appendLine("import androidx.test.platform.app.InstrumentationRegistry")
        appendLine("import org.junit.Test")
        appendLine("import org.junit.runner.RunWith")
        appendLine("import java.io.File")
        appendLine()
        appendLine("@RunWith(AndroidJUnit4::class)")
        appendLine("class CodeviewBatchTest {")
        appendLine()
        appendLine("    @Test")
        appendLine("    fun renderAll() = runAndroidComposeUiTest(activityClass = $activityFqn::class.java) {")
        appendLine("        val ctx = InstrumentationRegistry.getInstrumentation().targetContext")
        appendLine("        val outputDir = File(ctx.externalCacheDir, \"$onDeviceSubdir\").apply { mkdirs() }")
        appendLine("        File(outputDir, \"__codeview_published__\").writeText(\"1\")")
        appendLine("        CodeviewRuntime.runBatch(")
        appendLine("            uiTest = this,")
        appendLine("            outputDir = outputDir,")
        appendLine("            registry = PREVIEW_REGISTRY,")
        appendLine("            skippedIds = SKIPPED_IDS,")
        appendLine("        )")
        appendLine("    }")
        appendLine("}")
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
