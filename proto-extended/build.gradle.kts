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
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    implementation("com.squareup.wire:wire-schema:6.4.5")
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("protoExtended") {
            id = "com.rohittp.plugables.proto-extended"
            displayName = "ProtoExtended"
            description = "Generates Kotlin extension properties from proto enums — multiplatform metadata properties plus Android string/drawable accessors."
            tags = listOf("kotlin", "kotlin-multiplatform", "protobuf", "wire", "codegen")
            implementationClass = "com.rohittp.plugables.protoextended.ProtoExtendedPlugin"
        }
    }
}

mavenPublishing {
    // publishToMavenCentral(automaticRelease = true) and signing are configured centrally in
    // the root build.gradle.kts `subprojects { }` block.

    pom {
        name.set("ProtoExtended")
        description.set("Generates Kotlin extension properties from proto enums — multiplatform metadata properties plus Android string/drawable accessors.")
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
