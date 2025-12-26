import buildsrc.convention.santorini.tasks.UploadAndExecuteSshTask

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    kotlin("plugin.serialization")
//    alias("kotlin.serialization")
}

dependencies {
    api("io.fabric8:kubernetes-client:7.4.0")
    api(project(":share:santorini-model"))
    implementation(libs.kotlin.kotlinLogging)
    // Apply the kotlinx bundle of dependencies from the version catalog (`gradle/libs.versions.toml`).
    implementation(libs.bundles.kotlinxEcosystem)
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:6.0.5")
}

tasks.register<UploadAndExecuteSshTask>("uploadJar") {
    group = "deployment"
    description = "Upload JAR via SFTP after build"

//    layout.buildDirectory.dir("libs").map {
//        it.asFileTree.matching {
//            include("*.jar")
//        }
//    }

    val jarTask = tasks.named<Jar>("jar")
    dependsOn(jarTask)

    targetFiles.from(jarTask.flatMap { it.archiveFile })
}
// 让 build 执行完自动触发上传
tasks.named("build").configure {
    finalizedBy("uploadJar")
}
