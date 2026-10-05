import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    kotlin("plugin.serialization")
//    alias("kotlin.serialization")
}

tasks.withType<KotlinCompilationTask<*>> {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
//        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

dependencies {
    // k8s-client 已 api 暴露 utils（fabric8、santorini-model）
    api(project(":libs:k8s-client"))
    implementation(libs.kotlin.kotlinLogging)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation(libs.aliyun.cas)
    implementation(libs.aliyun.alb)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinxCoroutines.test)
    testImplementation("io.mockk:mockk:1.14.6")
    testImplementation("io.kotest:kotest-assertions-core:6.0.5")
}
