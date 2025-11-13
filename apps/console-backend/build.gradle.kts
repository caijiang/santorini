import buildsrc.convention.santorini.tasks.UploadAndExecuteSshTask

plugins {
    id("buildsrc.convention.kotlin-jvm")
//    id("buildsrc.convention.upload-image")
    id("io.ktor.plugin")
//    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinPluginSerialization)
//    id("com.google.cloud.tools.jib") version "3.4.0"
}

application {
    mainClass = "io.ktor.server.cio.EngineMain"
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
    rootDir = rootProject.projectDir
    dependsOn("buildImage")
}

tasks.named("buildImage") {
    finalizedBy("uploadAndExecuteSsh")
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
    }
}

dependencies {
    implementation("io.github.caijiang.everest.share:common:1.0-SNAPSHOT")
    implementation(project(":utils"))
    //    https://github.com/fabric8io/kubernetes-client
    implementation("io.fabric8:kubernetes-client:7.4.0")
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.apache)
    implementation(libs.ktor.server.cio)
    @Suppress("VulnerableLibrariesLocal")
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.config.yaml)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}
