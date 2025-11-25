package buildsrc.convention.api

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.contains
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File

/**
 * npm 包的目的地
 * @author CJ
 */
interface NpmPackagesDestination {
    /**
     * @return 包目录
     */
    fun packageHome(rootDir: File, moduleName: String): File

    /**
     * 复制 `package.json` 文件
     */
    fun copyPackageJsonFile(moduleName: String, srcJsonFile: File, destJsonFile: File)
}

/**
 * 传统复制
 */
class ClassicsNpmPackagesDestination(
    private val homeName: String
) : NpmPackagesDestination {
    override fun packageHome(rootDir: File, moduleName: String): File {
        return File(rootDir, "$homeName/node_modules/$moduleName")
    }

    override fun copyPackageJsonFile(moduleName: String, srcJsonFile: File, destJsonFile: File) {
        srcJsonFile.copyTo(destJsonFile, true)
    }
}

/**
 * 其实是 [NxPublishablePackagesDestination] 但是为了node构件方便(无需额外声明 path)直接在 node_modules 把包塞进去
 */
class NxPublishablePackagesShadowDestination(
    private val homeName: String,
    private val organization: String = "santorini"
) : NpmPackagesDestination {
    override fun packageHome(rootDir: File, moduleName: String): File {
        return File(rootDir, "$homeName/node_modules/@${organization}/generated-$moduleName")
    }

    override fun copyPackageJsonFile(moduleName: String, srcJsonFile: File, destJsonFile: File) {
        srcJsonFile.copyTo(destJsonFile, true)
    }
}

/**
 * 可以被发布的,其名称也变更为 `generated-XXX`,路径也被固定为 `generated/generated-XXX`
 */
class NxPublishablePackagesDestination(
    private val homeName: String,
    private val organization: String = "santorini"
) : NpmPackagesDestination {
    override fun packageHome(rootDir: File, moduleName: String): File {
        val home = File(rootDir, "$homeName/generated/generated-$moduleName")
        home.mkdirs()
        val projectJson = home.resolve("project.json")
        if (!projectJson.exists()) {
            jacksonObjectMapper().writer(PackageJsonPrettyPrinter())
                .writeValue(
                    projectJson, mapOf(
//                    "name" to "generated-$moduleName"
                        "projectType" to "library"
                    )
                )
        }
        return home
    }

    override fun copyPackageJsonFile(moduleName: String, srcJsonFile: File, destJsonFile: File) {
        val prettyPrinter = PackageJsonPrettyPrinter()

        val srcRoot = jacksonObjectMapper().readTree(srcJsonFile)
        (srcRoot as ObjectNode).put("name", "@${organization}/generated-$moduleName")
        // 保留原来 package-json 的 name 和 version
        if (destJsonFile.exists()) {
            val destRoot = jacksonObjectMapper().readTree(destJsonFile)
            if (destRoot.contains("version")) {
                srcRoot.replace("version", destRoot["version"])
            }
            jacksonObjectMapper().writer(prettyPrinter).writeValue(destJsonFile, srcRoot)
        } else {
            jacksonObjectMapper().writer(prettyPrinter).writeValue(destJsonFile, srcRoot)
        }
    }

}