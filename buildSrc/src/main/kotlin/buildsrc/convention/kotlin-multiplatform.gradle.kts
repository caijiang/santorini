package buildsrc.convention

import buildsrc.convention.api.MultiplatformExt
import buildsrc.convention.api.NxPublishablePackagesDestination
import buildsrc.convention.api.NxPublishablePackagesShadowDestination
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask


plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val libs: VersionCatalog = project.rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    js(IR) {
//        moduleName = "example"
        // To build distributions for and run tests on browser or Node.js use one or both of:
        browser {
            binaries.executable()
            generateTypeScriptDefinitions()
        }
//        nodejs()
//        generateTypeScriptDefinitions()
//        useEsModules()
    }
    jvmToolchain(21)
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                val kotlinxDatetime = libs.findVersion("kotlinxDatetime").get()
                implementation(libs.findLibrary("kotlinxSerialization").get())
                implementation(libs.findLibrary("kotlinxCoroutines").get())
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetime")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test")) // 通用 Kotlin 测试库
//                implementation(libs.findLibrary("kotlinxCoroutines-test").get())
            }
        }
        // JVM 平台特定配置
        val jvmTest by getting {
            dependencies {
//                implementation(libs.findLibrary("mockk").get())
//                implementation(libs.findLibrary("kotlinxCoroutines-test").get())
            }
        }
    }
}

tasks.withType<KotlinCompilationTask<*>> {
    compilerOptions {
        optIn.add("kotlin.js.ExperimentalJsExport")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

private fun copyFiles(dir: Directory, targetDir: File) {
    // 开发服务器大多有构建缓存，应该支持自动清理构建缓存，如果实在不行 跑的时候 一定要注意忽视缓存，比如 vite 就支持 加入关键字 --force https://vite.dev/config/shared-options.html#cachedir
    dir.asFile.copyRecursively(targetDir, overwrite = true)
}

extensions.add(MultiplatformExt::class, "multiplatformExt", object : MultiplatformExt {

    override fun copyNpmPackageToNodeAfter(moduleName: String, taskName: String) {
        tasks.named(taskName) {
            notCompatibleWithConfigurationCache("并不兼容")
            doLast {
                // 目的地类型 也分为两种: 1: 普通的 直接复制就可以了 2: nx约定的 这些包是需要发布的
                val nodeHomes = listOf(
//                    ClassicsNpmPackagesDestination("maple"),
                    NxPublishablePackagesShadowDestination("node"),
                    NxPublishablePackagesDestination("node")
                )
                nodeHomes.forEach { dest ->
                    val targetDir = dest.packageHome(rootDir, moduleName)
                    logger.info("Deploying to ${targetDir.absolutePath}")
                    targetDir.mkdirs()
                    // js 代码在哪里?
                    copyFiles(
                        project.layout.buildDirectory.get().dir("compileSync")
                            .dir("js").dir("main").dir("productionExecutable").dir("kotlin"), targetDir
                    )
                    dest.copyPackageJsonFile(
                        moduleName,
                        project.layout.buildDirectory.get().dir("tmp").dir("jsPublicPackageJson")
                            .file("package.json").asFile,
                        File(targetDir, "package.json")
                    )
                }
            }
        }
    }
})
