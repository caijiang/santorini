plugins {
    // The Kotlin DSL plugin provides a convenient way to develop convention plugins.
    // Convention plugins are located in `src/main/kotlin`, with the file extension `.gradle.kts`,
    // and are applied in the project's `build.gradle.kts` files as required.
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Add a dependency on the Kotlin Gradle plugin, so that convention plugins can apply it.
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.kotlinSerializationPlugin)
    implementation(libs.ktorPlugin)
//    implementation("io.ktor.plugin:io.ktor.plugin.gradle.plugin:3.3.2")
    implementation("com.jcraft:jsch:0.1.55")
    @Suppress("VulnerableLibrariesLocal")
    implementation("org.hidetake.ssh:org.hidetake.ssh.gradle.plugin:2.10.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.3")
}
