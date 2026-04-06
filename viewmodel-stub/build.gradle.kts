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
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        create("viewModelStub") {
            id = "com.rohittp.plugables.viewmodel-stub"
            implementationClass = "com.rohittp.plugables.viewmodelstub.ViewModelStubPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
