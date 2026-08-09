package com.rohittp.plugables.protoextended

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Functional tests. Both tasks are registered unconditionally and only the source-set
 * wiring is gated on the Kotlin/Android plugins, so the real tasks can be driven with
 * no other plugin applied.
 */
class ProtoExtendedTestKitTest {

    private fun scaffold(tmp: File, buildScript: String) {
        File(tmp, "settings.gradle.kts").writeText("""rootProject.name = "fixture"""")
        File(tmp, "build.gradle.kts").writeText(buildScript.trimIndent())

        val proto = File(tmp, "proto").apply { mkdirs() }
        ProtoFixtures.write(proto, "gen_options.proto", ProtoFixtures.GEN_OPTIONS)
        ProtoFixtures.write(
            proto, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            import "gen_options.proto";
            import "google/protobuf/descriptor.proto";
            option java_package = "com.example.model";

            message RatioMeta {
              int32 width = 1;
            }

            extend google.protobuf.EnumValueOptions {
              optional RatioMeta ratio_meta = 50001;
            }

            enum AspectRatio {
              option (gen.resources) = { display_name: true, icon: true };
              RATIO_1_1 = 0 [(ratio_meta) = { width: 1 }];
              RATIO_16_9 = 1 [(ratio_meta) = { width: 16 }];
            }
            """,
        )
    }

    private val bothBlocks = """
        plugins { id("com.rohittp.plugables.proto-extended") }
        protoExtended {
            metadata {
                protoDir.set(layout.projectDirectory.dir("proto"))
                basePackage.set("com.example.generated")
            }
            androidResources {
                protoDir.set(layout.projectDirectory.dir("proto"))
                basePackage.set("com.example.generated.res")
                rPackage.set("com.example.app")
            }
        }
    """

    private fun runner(tmp: File, vararg args: String) =
        GradleRunner.create()
            .withProjectDir(tmp)
            .withPluginClasspath()
            .withArguments(*args)

    @Test
    fun `metadata task generates the expected file`(@TempDir tmp: File) {
        scaffold(tmp, bothBlocks)

        val result = runner(tmp, "generateProtoMetadata").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateProtoMetadata")!!.outcome)
        val generated = File(
            tmp,
            "build/generated/source/protoExtended/metadata/com/example/generated/ProtoEnumMetadata.kt",
        )
        assertTrue(generated.isFile, "expected generated file at ${generated.path}")
        val text = generated.readText()
        assertTrue(text.contains("val AspectRatio.width: Int"), text)
        assertTrue(text.contains("import com.example.model.AspectRatio"), text)
    }

    @Test
    fun `android resources task generates the expected file`(@TempDir tmp: File) {
        scaffold(tmp, bothBlocks)

        val result = runner(tmp, "generateProtoAndroidResources").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateProtoAndroidResources")!!.outcome)
        val generated = File(
            tmp,
            "build/generated/source/protoExtended/androidResources/com/example/generated/res/ProtoEnumResources.kt",
        )
        assertTrue(generated.isFile, "expected generated file at ${generated.path}")
        val text = generated.readText()
        assertTrue(text.contains("import com.example.app.R"), text)
        assertTrue(text.contains("AspectRatio.RATIO_1_1 -> R.string.ratio_1_1"), text)
        assertTrue(text.contains("AspectRatio.RATIO_1_1 -> R.drawable.ratio_1_1"), text)
    }

    @Test
    fun `second run is up-to-date and a proto edit re-executes`(@TempDir tmp: File) {
        scaffold(tmp, bothBlocks)

        assertEquals(
            TaskOutcome.SUCCESS,
            runner(tmp, "generateProtoMetadata").build().task(":generateProtoMetadata")!!.outcome,
        )
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            runner(tmp, "generateProtoMetadata").build().task(":generateProtoMetadata")!!.outcome,
        )

        File(tmp, "proto/sample.proto").appendText("\nenum Extra { E = 0; }\n")

        assertEquals(
            TaskOutcome.SUCCESS,
            runner(tmp, "generateProtoMetadata").build().task(":generateProtoMetadata")!!.outcome,
        )
    }

    @Test
    fun `unconfigured block reports NO-SOURCE`(@TempDir tmp: File) {
        scaffold(
            tmp,
            """
            plugins { id("com.rohittp.plugables.proto-extended") }
            protoExtended {
                metadata {
                    protoDir.set(layout.projectDirectory.dir("proto"))
                    basePackage.set("com.example.generated")
                }
            }
            """,
        )

        val result = runner(tmp, "generateProtoAndroidResources").build()

        assertEquals(
            TaskOutcome.NO_SOURCE,
            result.task(":generateProtoAndroidResources")!!.outcome,
        )
    }

    @Test
    fun `configuration cache is reused on the second run`(@TempDir tmp: File) {
        scaffold(tmp, bothBlocks)

        runner(tmp, "generateProtoMetadata", "--configuration-cache").build()
        val second = runner(tmp, "generateProtoMetadata", "--configuration-cache").build()

        assertTrue(
            second.output.contains("Reusing configuration cache"),
            "expected configuration cache reuse, output was:\n${second.output}",
        )
    }

    @Test
    fun `validation failure fails the build with a readable message`(@TempDir tmp: File) {
        scaffold(tmp, bothBlocks)
        File(tmp, "proto/sample.proto").writeText(
            """
            syntax = "proto3";
            package ta;
            import "google/protobuf/descriptor.proto";

            message RatioMeta { int32 width = 1; }

            extend google.protobuf.EnumValueOptions {
              optional RatioMeta ratio_meta = 50001;
            }

            enum AspectRatio {
              RATIO_1_1 = 0 [(ratio_meta) = { width: 1 }];
              RATIO_16_9 = 1;
            }
            """.trimIndent(),
        )

        val result = runner(tmp, "generateProtoMetadata").buildAndFail()

        assertTrue(result.output.contains("RATIO_16_9"), result.output)
        assertTrue(result.output.contains("Every constant must set the option"), result.output)
    }
}
