import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    kotlin("plugin.serialization")
//    alias("kotlin.serialization")
    id("buildsrc.convention.plugin-common-ext")
}

tasks.withType<KotlinCompilationTask<*>> {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation("org.apache.sshd:sshd-core:2.14.0")
    implementation(libs.kotlin.kotlinLogging)
    implementation(libs.bundles.kotlinxEcosystem)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutines.test)
    testImplementation("io.kotest:kotest-assertions-core:6.0.5")
    testImplementation(libs.logback.classic)

}