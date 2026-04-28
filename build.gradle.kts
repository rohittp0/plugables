plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("android") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
    id("com.android.application") version "9.2.0" apply false
    id("com.android.library") version "9.2.0" apply false
    id("com.vanniktech.maven.publish") version "0.33.0" apply false
}

subprojects {
    group = "com.rohittp.plugables"

    // vanniktech maven-publish + Maven Central publish flow isn't config-cache compatible yet:
    // https://github.com/gradle/gradle/issues/22779. Mark publish tasks as opt-out so the rest
    // of the build still benefits from the config cache.
    tasks.withType(org.gradle.api.publish.maven.tasks.PublishToMavenRepository::class.java).configureEach {
        notCompatibleWithConfigurationCache(
            "Maven Central publishing isn't config-cache compatible yet — gradle/gradle#22779."
        )
    }
}
