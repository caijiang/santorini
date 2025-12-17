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
    implementation("io.github.oshai:kotlin-logging:7.0.13")
    // Apply the kotlinx bundle of dependencies from the version catalog (`gradle/libs.versions.toml`).
    implementation(libs.bundles.kotlinxEcosystem)
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:6.0.5")
}

tasks.register<UploadAndExecuteSshTask>("uploadJar") {
    group = "deployment"
    description = "Upload JAR via SFTP after build"

    targetFiles.from(layout.buildDirectory.dir("libs").get().asFileTree.matching {
        include("*.jar")
    })
    rootDir = rootProject.projectDir
    dependsOn("build")
}
// 让 build 执行完自动触发上传
tasks.named("build").configure {
    finalizedBy("uploadJar")
}

//tasks.register("uploadJar") {
//    notCompatibleWithConfigurationCache("并不兼容")
//    group = "deployment"
//    description = "Upload JAR via SFTP after build"
//
//    // 让这个任务在构建完成后执行
//    dependsOn("build")
//
//    doLast {
//        val configFile = rootProject.file("local-upload-sftp-config.json")
//        if (!configFile.exists()) {
//            println("Missing ${configFile.absolutePath}")
//            return@doLast
//        }
//
//        // 解析 JSON 配置
//        val configText = configFile.readText()
//        val json = groovy.json.JsonSlurper().parseText(configText) as Map<*, *>
//
//        val host = json["host"] as String
//        val port = (json["port"] as Number).toInt()
//        val username = json["username"] as String
//        val privateKeyPath = json["privateKeyPath"] as String
//        val remoteDir = json["remoteDir"] as String
//
//        // 查找 JAR 文件
//        val jarFile = layout.buildDirectory.file("libs").get().asFile.apply {
//            println(this.absolutePath)
//        }.listFiles()?.find { it.extension == "jar" }
//            ?: throw GradleException("No JAR file found in build/libs")
//
//        println("📦 Uploading ${jarFile.name} to $username@$host:$remoteDir ...")
//
//        // 使用 JSch 上传
//        val jsch = JSch()
//        jsch.addIdentity(privateKeyPath)
//        val session = jsch.getSession(username, host, port)
//        session.setConfig("StrictHostKeyChecking", "no")
//        session.connect()
//
//        val channel = session.openChannel("sftp") as ChannelSftp
//        channel.connect()
//        channel.cd(remoteDir)
//        channel.put(jarFile.absolutePath, jarFile.name)
//        channel.disconnect()
//        session.disconnect()
//
//        println("✅ Upload complete: $remoteDir/${jarFile.name}")
//    }
//}

