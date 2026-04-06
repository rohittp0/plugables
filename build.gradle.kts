subprojects {
    repositories {
        google()
        mavenCentral()
    }

    // Configure GitHub Packages publish target once maven-publish is applied
    // (kotlin-dsl applies it via java-gradle-plugin — withId defers safely)
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/rohittp0/plugables")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                        password = System.getenv("GITHUB_TOKEN")
                    }
                }
            }
        }
    }
}
