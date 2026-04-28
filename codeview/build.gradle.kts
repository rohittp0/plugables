import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    id("com.vanniktech.maven.publish") version "0.36.0"
}

version = "0.1.0"

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
    signAllPublications()

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

// vanniktech maven-publish + Maven Central publish flow isn't config-cache compatible yet:
// https://github.com/gradle/gradle/issues/22779. Mark publish tasks as opt-out so the rest of
// the build still benefits from the config cache.
tasks.withType(org.gradle.api.publish.maven.tasks.PublishToMavenRepository::class.java).configureEach {
    notCompatibleWithConfigurationCache(
        "Maven Central publishing isn't config-cache compatible yet — gradle/gradle#22779."
    )
}
