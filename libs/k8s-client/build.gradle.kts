plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    kotlin("plugin.serialization")
//    alias("kotlin.serialization")
}

dependencies {
    // utils 已经 api 暴露了 fabric8 与 santorini-model，这里不重复声明
    api(project(":utils"))
    implementation(libs.kotlin.kotlinLogging)
    implementation(libs.bundles.kotlinxEcosystem)
}
