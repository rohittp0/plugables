import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.0"
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
    compileOnly("com.android.tools.build:gradle:8.5.0")
    implementation("org.yaml:snakeyaml:2.2")
    testImplementation(kotlin("test"))
}

gradlePlugin {
    website = "https://rohittp.com/plugables"
    vcsUrl = "https://github.com/rohittp0/plugables.git"

    plugins {
        create("typedEvents") {
            id = "com.rohittp.plugables.typed-events"
            displayName = "TypedEvents"
            description = "Generates type-safe Kotlin event classes from a YAML schema for Android projects."
            tags = listOf("android", "kotlin", "events", "codegen")
            implementationClass = "com.rohittp.plugables.typedevents.TypedEventsPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
