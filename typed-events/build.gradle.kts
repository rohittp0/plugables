import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    id("com.vanniktech.maven.publish")
}

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
    compileOnly("com.android.tools.build:gradle:9.2.0")
    implementation("org.yaml:snakeyaml:2.6")
    testImplementation(kotlin("test"))
}

gradlePlugin {
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

mavenPublishing {
    publishToMavenCentral()
    // Skip signing when signing keys aren't configured (local publishToMavenLocal).
    if (System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) {
        signAllPublications()
    }

    pom {
        name.set("TypedEvents")
        description.set("Generates type-safe Kotlin event classes from a YAML schema for Android projects.")
        url.set("https://github.com/rohittp0/plugables")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("rohittp0")
                name.set("Rohit T P")
                url.set("https://rohittp.com")
            }
        }
        scm {
            url.set("https://github.com/rohittp0/plugables")
            connection.set("scm:git:git://github.com/rohittp0/plugables.git")
            developerConnection.set("scm:git:ssh://git@github.com/rohittp0/plugables.git")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
