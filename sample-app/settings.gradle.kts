// sample-app is a STANDALONE Gradle build, not a subproject of the root.
// It dogfoods all three plugins by consuming them from mavenLocal.
//
// Bootstrap workflow:
//   ./gradlew :codeview:publishToMavenLocal \
//             :typed-events:publishToMavenLocal \
//             :viewmodel-stub:publishToMavenLocal
//
// Then run sample-app:
//   ./gradlew -p sample-app codeviewReportDebug
// or
//   gradle codeviewReportDebug   (from inside this directory)

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "sample-app"
