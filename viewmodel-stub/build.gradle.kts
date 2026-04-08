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
    testImplementation(kotlin("test"))
}

gradlePlugin {
    website = "https://rohittp.com/plugables/viewmodel-stub"
    vcsUrl = "https://github.com/rohittp0/plugables.git"

    plugins {
        create("viewModelStub") {
            id = "com.rohittp.plugables.viewmodel-stub"
            displayName = "ViewModelStub"
            description = "Generates interface + preview-safe stub classes from Android ViewModels annotated with @ViewModelStub."
            tags = listOf("android", "kotlin", "viewmodel", "compose", "preview")
            implementationClass = "com.rohittp.plugables.viewmodelstub.ViewModelStubPlugin"
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
