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
}

application {
    mainClass = "io.ktor.server.cio.EngineMain"
}

tasks.withType<KotlinCompilationTask<*>> {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
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
    //    https://github.com/fabric8io/kubernetes-client
    implementation("io.fabric8:kubernetes-client:7.4.0")
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.apache)
    implementation(libs.ktor.server.cio)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.config.yaml)
    implementation("io.ktor:ktor-client-encoding:3.3.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.2")
    implementation("io.ktor:ktor-serialization-jackson:3.3.2")
    implementation("io.github.oshai:kotlin-logging:7.0.13")
    implementation("io.ktor:ktor-server-call-logging:3.3.2")
//    https://www.jetbrains.com/help/exposed/exposed-modules.html#extension-modules
    implementation("org.jetbrains.exposed:exposed-core:1.0.0-rc-4")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.0.0-rc-4")
    implementation("org.jetbrains.exposed:exposed-json:1.0.0-rc-4")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:1.0.0-rc-4")
    implementation("org.jetbrains.exposed:exposed-migration-jdbc:1.0.0-rc-4")
    implementation("com.h2database:h2:2.3.232")
    implementation("io.insert-koin:koin-ktor:4.2.0-beta2")
    implementation("io.insert-koin:koin-logger-slf4j:4.2.0-beta2")
    implementation("io.ktor:ktor-server-host-common:3.3.2")
    implementation("io.ktor:ktor-server-status-pages:3.3.2")
    testImplementation("io.ktor:ktor-client-logging:3.3.2")
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
