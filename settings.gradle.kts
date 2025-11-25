// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

dependencyResolutionManagement {
    // Use Maven Central as the default repository (where Gradle will download dependencies) in all subprojects.
    @Suppress("UnstableApiUsage")
    repositories {
//        mavenLocal()
//        https://central.sonatype.org/publish/publish-portal-snapshots/#consuming-via-gradle
        maven {
            name = "Central Portal Snapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")

            // Only search this repository for the specific dependency
            content {
                includeVersion("io.github.caijiang.everest.share", "common", "1.0-SNAPSHOT")
                includeVersion("io.github.caijiang.everest.share", "common-jvm", "1.0-SNAPSHOT")
            }
        }
        mavenCentral()
    }
}

plugins {
    // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

// Include the `app` and `utils` subprojects in the build.
// If there are changes in only one of the projects, Gradle will rebuild only the one that has changed.
// Learn more about structuring projects with Gradle - https://docs.gradle.org/8.7/userguide/multi_project_builds.html
include(":utils")

rootProject.name = "santorini"
include("apps")
include("apps:console-backend")
findProject(":apps:console-backend")?.name = "console-backend"
include(":chart")
//findProject(":chart")?.name = "Santorini Chart"
include("share")
include("share:santorini-model")
findProject(":share:santorini-model")?.name = "santorini-model"
