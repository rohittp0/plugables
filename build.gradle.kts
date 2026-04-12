plugins {
    kotlin("jvm") version "2.3.0" apply false
    id("com.vanniktech.maven.publish") version "0.33.0" apply false
}

subprojects {
    group = "com.rohittp.plugables"

    repositories {
        google()
        mavenCentral()
    }
}
