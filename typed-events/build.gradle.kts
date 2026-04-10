import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.plugin.compatibility.compatibility

plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish")
}

group = "com.rohittp.plugables"
version = "1.0.0"

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    compileOnly("com.android.tools.build:gradle:9.1.0")
    implementation("org.yaml:snakeyaml:2.6")
    testImplementation(kotlin("test"))
}

gradlePlugin {
    website = "https://rohittp.com/plugables/typed-events"
    vcsUrl = "https://github.com/rohittp0/plugables"

    plugins {
        create("typedEvents") {
            id = "com.rohittp.plugables.typed-events"
            displayName = "TypedEvents"
            description = "Generates type-safe Kotlin event classes from a YAML schema for Android projects."
            tags = listOf("android", "kotlin", "events", "codegen")
            implementationClass = "com.rohittp.plugables.typedevents.TypedEventsPlugin"
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
