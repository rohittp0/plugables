import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    id("com.vanniktech.maven.publish")
}

version = "1.0.1"

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
    implementation("com.android.tools:sdk-common:32.2.0")           // VdPreview — vector drawable rasterization
    implementation("org.apache.xmlgraphics:batik-transcoder:1.17")  // SVG -> raster
    implementation("org.apache.xmlgraphics:batik-codec:1.17")       // PNG output codec for Batik
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("branchmark") {
            id = "com.rohittp.plugables.branchmark"
            displayName = "Branchmark"
            description = "Stamps the debug launcher icon with the current git branch — a suffix ribbon plus a prefix emoji — by reading the app's existing adaptive icon and generating only the banner overlay."
            tags = listOf("android", "kotlin", "git", "launcher-icon", "debug", "tooling")
            implementationClass = "com.rohittp.plugables.branchmark.BranchmarkPlugin"
        }
    }
}

mavenPublishing {
    // publishToMavenCentral(automaticRelease = true) and signing are configured centrally in
    // the root build.gradle.kts `subprojects { }` block.

    pom {
        name.set("Branchmark")
        description.set("Stamps the debug launcher icon with the current git branch — a suffix ribbon plus a prefix emoji — by reading the app's existing adaptive icon and generating only the banner overlay.")
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
