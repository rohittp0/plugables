package com.rohittp.plugables.typedevents

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypedEventsTestKitTest {

    @Test
    fun `KMP common source compiles for JVM and Kotlin Native`(@TempDir tmp: File) {
        File(tmp, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { gradlePluginPortal(); google(); mavenCentral() }
            }
            dependencyResolutionManagement {
                repositories { google(); mavenCentral() }
            }
            rootProject.name = "typed-events-kmp-fixture"
            """.trimIndent(),
        )
        File(tmp, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.3.21"
                id("com.rohittp.plugables.typed-events")
            }

            kotlin {
                jvm()
                iosSimulatorArm64()
            }

            typedEvents {
                specFile.set(layout.projectDirectory.file("analytics_events.yaml"))
            }
            """.trimIndent(),
        )
        File(tmp, "analytics_events.yaml").writeText(
            """
            - app_open: App entered the foreground
            - purchase_completed:
                info: User completed a purchase
                params:
                  placement_id:
                    type: String
                    info: Paywall placement identifier
                  price:
                    type: Double?
                    info: Nullable localized price
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(tmp)
            .withPluginClasspath()
            .withArguments("compileKotlinJvm", "compileKotlinIosSimulatorArm64", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateTypedEvents")!!.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinJvm")!!.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinIosSimulatorArm64")!!.outcome)
        val generated = File(
            tmp,
            "build/generated/source/typedEvents/main/com/rohittp/plugables/analytics/AnalyticsEventsFacade.kt",
        ).readText()
        assertTrue(generated.contains("object AnalyticsEvents"), generated)
        assertTrue(generated.contains("price: Double?"), generated)
    }
}
