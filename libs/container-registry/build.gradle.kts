plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    kotlin("plugin.serialization")
//    alias("kotlin.serialization")
}

dependencies {
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.apache)
    implementation("commons-codec:commons-codec:1.13")
    implementation(libs.kotlin.kotlinLogging)
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.client.contentNegotiation)

    testImplementation(libs.ktor.client.logging)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutines.test)
    testImplementation("io.kotest:kotest-assertions-core:6.0.5")
    testImplementation(libs.logback.classic)
}
