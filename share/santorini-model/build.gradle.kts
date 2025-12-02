import buildsrc.convention.santorini.tasks.UploadAndExecuteSshTask

plugins {
    id("buildsrc.convention.kotlin-multiplatform")
}

//kotlin {
//    js {
//        useEsModules()
//    }
//    sourceSets {
//        commonMain.dependencies {
//            implementation(libs.kotlinxCoroutines)
//            implementation(project(":share:common"))
//        }
//    }
//}

multiplatformExt {
    copyNpmPackageToNodeAfter("santorini-model")
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