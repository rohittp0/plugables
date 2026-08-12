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

        val values = File(tmp, "src/commonMain/composeResources/values/strings.xml")
        values.parentFile.mkdirs()
        values.writeText(
            """
            <resources>
                <string name="ratio_1_1">Square</string>
                <string name="ratio_16_9">Landscape</string>
            </resources>
            """.trimIndent(),
        )
        val drawable = File(tmp, "src/commonMain/composeResources/drawable/ratio_1_1.xml")
        drawable.parentFile.mkdirs()
        drawable.writeText("<vector />")
        File(drawable.parentFile, "ratio_16_9.xml").writeText("<vector />")
    }

    private val bothBlocks = """
        plugins { id("com.rohittp.plugables.proto-extended") }
        protoExtended {
            metadata {
                protoDir.set(layout.projectDirectory.dir("proto"))
                basePackage.set("com.example.generated")
            }
            resources {
                protoDir.set(layout.projectDirectory.dir("proto"))
                basePackage.set("com.example.generated.resources")
            }
        }
    """

    private fun runner(tmp: File, vararg args: String) =
        GradleRunner.create()
            .withProjectDir(tmp)
            .withPluginClasspath()
            .withArguments(*args)

    @Test
    fun `KMP common source compiles against real localized Compose resources`(@TempDir tmp: File) {
        scaffold(
            tmp,
            """
            plugins {
                kotlin("multiplatform") version "2.3.21"
                id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
                id("org.jetbrains.compose") version "1.12.0-alpha02"
                id("com.rohittp.plugables.proto-extended")
            }

            repositories {
                google()
                mavenCentral()
                maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
            }

            kotlin {
                jvm()
                sourceSets.commonMain.dependencies {
                    implementation(compose.runtime)
                    implementation(compose.components.resources)
                }
            }

            compose.resources {
                packageOfResClass = "com.example.generated.resources"
            }

            protoExtended {
                resources {
                    protoDir.set(layout.projectDirectory.dir("proto"))
                    basePackage.set("com.example.generated.resources")
                }
            }
            """,
        )

        val model = File(tmp, "src/commonMain/kotlin/com/example/model/AspectRatio.kt")
        model.parentFile.mkdirs()
        model.writeText(
            """
            package com.example.model

            enum class AspectRatio { RATIO_1_1, RATIO_16_9 }
            """.trimIndent(),
        )

        val values = File(tmp, "src/commonMain/composeResources/values/strings.xml")
        values.parentFile.mkdirs()
        values.writeText(
            """
            <resources>
                <string name="ratio_1_1">Square</string>
                <string name="ratio_16_9">Landscape</string>
            </resources>
            """.trimIndent(),
        )
        val regionalValues = File(
            tmp,
            "src/commonMain/composeResources/values-pt-rPT/strings.xml",
        )
        regionalValues.parentFile.mkdirs()
        regionalValues.writeText(
            """
            <resources>
                <string name="ratio_1_1">Quadrado PT</string>
                <string name="ratio_16_9">Horizontal PT</string>
            </resources>
            """.trimIndent(),
        )

        val drawable = File(tmp, "src/commonMain/composeResources/drawable/ratio_1_1.xml")
        drawable.parentFile.mkdirs()
        drawable.writeText(
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="24dp" android:height="24dp"
                android:viewportWidth="24" android:viewportHeight="24">
                <path android:fillColor="#FFFFFFFF" android:pathData="M2,2h20v20h-20z"/>
            </vector>
            """.trimIndent(),
        )
        File(drawable.parentFile, "ratio_16_9.xml").writeText(drawable.readText())

        val result = runner(tmp, "compileKotlinJvm", "--stacktrace").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateProtoResources")!!.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinJvm")!!.outcome)
        val generated = File(
            tmp,
            "build/generated/source/protoExtended/resources/com/example/generated/resources/ProtoEnumResources.kt",
        )
        val source = generated.readText()
        assertTrue(source.contains("val AspectRatio.displayName: StringResource"), source)
        assertTrue(source.contains("val AspectRatio.icon: DrawableResource"), source)
    }

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
    fun `compose resources task generates the expected file`(@TempDir tmp: File) {
        scaffold(tmp, bothBlocks)

        val result = runner(tmp, "generateProtoResources").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateProtoResources")!!.outcome)
        val generated = File(
            tmp,
            "build/generated/source/protoExtended/resources/com/example/generated/resources/ProtoEnumResources.kt",
        )
        assertTrue(generated.isFile, "expected generated file at ${generated.path}")
        val text = generated.readText()
        assertTrue(text.contains("val AspectRatio.displayName: StringResource"), text)
        assertTrue(text.contains("AspectRatio.RATIO_1_1 -> Res.string.ratio_1_1"), text)
        assertTrue(text.contains("AspectRatio.RATIO_1_1 -> Res.drawable.ratio_1_1"), text)
    }

    @Test
    fun `renamed or missing base resource fails generation with the exact enum and resource`(
        @TempDir tmp: File,
    ) {
        scaffold(tmp, bothBlocks)
        File(tmp, "src/commonMain/composeResources/values/strings.xml").writeText(
            """
            <resources>
                <string name="ratio_1_1">Square</string>
                <string name="ratio_16_9_old">Landscape</string>
            </resources>
            """.trimIndent(),
        )
        File(tmp, "src/commonMain/composeResources/drawable/ratio_16_9.xml")
            .renameTo(File(tmp, "src/commonMain/composeResources/drawable/ratio_16_9_old.xml"))

        val result = runner(tmp, "generateProtoResources", "--stacktrace").buildAndFail()

        assertTrue(
            result.output.contains(
                "ta.AspectRatio.RATIO_16_9 expects string `ratio_16_9`",
            ),
            result.output,
        )
        assertTrue(
            result.output.contains(
                "ta.AspectRatio.RATIO_16_9 expects drawable `ratio_16_9`",
            ),
            result.output,
        )
        assertTrue(
            result.output.contains("Add or rename the base Compose resources"),
            result.output,
        )
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

        val result = runner(tmp, "generateProtoResources").build()

        assertEquals(
            TaskOutcome.NO_SOURCE,
            result.task(":generateProtoResources")!!.outcome,
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
