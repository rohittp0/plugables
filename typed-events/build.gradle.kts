import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    `maven-publish`
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
    plugins {
        create("typedEvents") {
            id = "com.rohittp.plugables.typed-events"
            implementationClass = "com.rohittp.plugables.typedevents.TypedEventsPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
