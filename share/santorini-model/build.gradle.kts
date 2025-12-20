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