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
    implementation(libs.kotlin.kotlinLogging)
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.ktor.server.core)
    implementation("io.insert-koin:koin-ktor:4.2.0-beta2")
    implementation(libs.ktor.client.core)

    testImplementation(libs.ktor.client.logging)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutines.test)
    testImplementation("io.kotest:kotest-assertions-core:6.0.5")
    testImplementation(libs.logback.classic)
}
