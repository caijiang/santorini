import buildsrc.convention.santorini.tasks.UploadAndExecuteSshTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("buildsrc.convention.kotlin-jvm")
//    id("buildsrc.convention.upload-image")
    id("io.ktor.plugin")
//    alias(libs.plugins.ktor)
    kotlin("plugin.serialization")
//    alias(libs.plugins.kotlinPluginSerialization)
//    id("com.google.cloud.tools.jib") version "3.4.0"
    id("buildsrc.convention.plugin-common-ext")
}

application {
    mainClass = "io.ktor.server.cio.EngineMain"
}

tasks.withType<KotlinCompilationTask<*>> {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
//        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

//jib{
//    from{
//        platforms.get().forEach {
//            println(it.architecture)
//            println(it.os)
//        }
//    }
//}

tasks.register<UploadAndExecuteSshTask>("uploadAndExecuteSsh") {
    targetFiles.from(layout.buildDirectory.file("jib-image.tar"))
    dependsOn("buildImage")
}

tasks.named("buildImage") {
    finalizedBy("uploadAndExecuteSsh")
}
tasks.named("test") {
    finalizedBy(":charts:santorini:test")
}

//tasks.named("clean") {
//    finalizedBy(tasks.named<UploadAndExecuteSshTask>("uploadAndExecuteSsh").apply {
//        this.get().targetFiles.from(layout.files("README.md"))
//        this.get().rootDir.set(rootProject.layout.projectDirectory)
//    })
//}

//设计一个插件或者任务，它允许在打包后上传到特定服务器，然后执行加载的指令，最好一切都可配置

ktor {
    docker {
        localImageName = "santorini-console-backend"
        imageTag = findProperty("dockerVersion")?.toString() ?: "latest"
    }
}

dependencies {
    implementation("io.github.caijiang.everest.share:common:1.0-SNAPSHOT")
    implementation(project(":utils"))
    implementation(project(":share:santorini-model"))
    implementation(project(":libs:container-registry"))
    implementation(libs.bundles.fabric8Kubernetes)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.apache)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.server.cio)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.kotlin.kotlinLogging)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.json)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.migration.jdbc)
    implementation("com.h2database:h2:2.3.232")
    implementation(libs.bundles.koin)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.status.pages)
    testImplementation(libs.ktor.client.logging)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
    testImplementation("io.mockk:mockk:1.14.6")
    testImplementation("io.kotest:kotest-assertions-core:6.0.5")
    testImplementation("org.testcontainers:mysql:1.21.3") {
        testImplementation("org.apache.commons:commons-compress:1.26.0")
        testImplementation("org.apache.commons:commons-lang3:3.18.0")
    }
    runtimeOnly("com.mysql:mysql-connector-j:9.5.0")
}
