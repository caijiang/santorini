package buildsrc.convention

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

// 1.0-SNAPSHOT 2.4.0
val commonExtVersion = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    if (commonExtVersion.contains("SNAPSHOT")) {
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }
}

dependencies {
    implementation("io.github.caijiang:common-ext-jakarta:$commonExtVersion")
}
