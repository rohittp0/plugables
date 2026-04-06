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
    testImplementation(kotlin("test"))
}

gradlePlugin {
    website = "https://github.com/rohittp0/plugables"
    vcsUrl = "https://github.com/rohittp0/plugables.git"

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

tasks.test {
    useJUnitPlatform()
}
