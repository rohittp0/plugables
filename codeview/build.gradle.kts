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
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("codeView") {
            id = "com.rohittp.plugables.codeview"
            displayName = "CodeView"
            description = "Generates an interactive HTML report of @Preview screens with per-composable click-to-IDE overlays."
            tags = listOf("android", "kotlin", "compose", "preview", "report", "tooling")
            implementationClass = "com.rohittp.plugables.codeview.CodeViewPlugin"
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
        name.set("CodeView")
        description.set("Generates an interactive HTML report of @Preview screens with per-composable click-to-IDE overlays.")
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
