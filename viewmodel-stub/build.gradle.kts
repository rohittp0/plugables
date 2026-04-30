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
    compileOnly("com.android.tools.build:gradle:9.1.0")
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        create("viewModelStub") {
            id = "com.rohittp.plugables.viewmodel-stub"
            displayName = "ViewModelStub"
            description = "Generates interface + preview-safe stub classes from Android ViewModels annotated with @ViewModelStub."
            tags = listOf("android", "kotlin", "viewmodel", "compose", "preview")
            implementationClass = "com.rohittp.plugables.viewmodelstub.ViewModelStubPlugin"
        }
    }
}

mavenPublishing {
    // publishToMavenCentral(automaticRelease = true) and signing are configured centrally in
    // the root build.gradle.kts `subprojects { }` block.

    pom {
        name.set("ViewModelStub")
        description.set("Generates interface + preview-safe stub classes from Android ViewModels annotated with @ViewModelStub.")
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
