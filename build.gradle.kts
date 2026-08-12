import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    kotlin("jvm") version "2.3.0" apply false
    id("com.vanniktech.maven.publish") version "0.33.0" apply false
}

val r2Endpoint = providers.environmentVariable("R2_ENDPOINT")
    .orElse("https://83e781551eacec848a6283c8e17d33e0.r2.cloudflarestorage.com")
val r2Bucket = providers.environmentVariable("R2_BUCKET").orElse("maven")

// Gradle's S3 transport supports custom S3-compatible endpoints through this property.
// Cloudflare R2 accepts the transport without requiring S3 ACL support (verified 2026-08-12).
System.setProperty("org.gradle.s3.endpoint", r2Endpoint.get())

subprojects {
    group = "com.rohittp.plugables"

    // Remote Maven publishing isn't config-cache compatible yet:
    // https://github.com/gradle/gradle/issues/22779. Mark publish tasks as opt-out so the rest
    // of the build still benefits from the config cache.
    tasks.withType(PublishToMavenRepository::class.java).configureEach {
        notCompatibleWithConfigurationCache(
            "Remote Maven publishing isn't config-cache compatible yet — gradle/gradle#22779."
        )
    }

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "R2"
                    url = uri("s3://${r2Bucket.get()}")
                    credentials(AwsCredentials::class) {
                        accessKey = providers.environmentVariable("R2_ACCESS_KEY_ID").orNull
                        secretKey = providers.environmentVariable("R2_SECRET_ACCESS_KEY").orNull
                    }
                }
            }
        }
    }

    // Centralised signing config — each plugin only needs its own `pom { }` block.
    // Local `publishToMavenLocal` remains unsigned unless a signing key is provided.
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            if (System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) {
                signAllPublications()
            }
        }
    }
}
