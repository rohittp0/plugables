import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    id("com.vanniktech.maven.publish")
}

version = "2.0.0"

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
    implementation("com.squareup.wire:wire-schema:6.4.5")
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("protoExtended") {
            id = "com.rohittp.plugables.proto-extended"
            displayName = "ProtoExtended"
            description = "Generates common Kotlin metadata and Compose Multiplatform resource properties from proto enums."
            tags = listOf("kotlin", "kotlin-multiplatform", "protobuf", "wire", "codegen")
            implementationClass = "com.rohittp.plugables.protoextended.ProtoExtendedPlugin"
        }
    }
}

mavenPublishing {
    // R2 publishing and signing are configured centrally in
    // the root build.gradle.kts `subprojects { }` block.

    pom {
        name.set("ProtoExtended")
        description.set("Generates common Kotlin metadata and Compose Multiplatform resource properties from proto enums.")
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
