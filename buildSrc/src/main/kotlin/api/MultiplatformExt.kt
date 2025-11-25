package buildsrc.convention.api

/**
 * @author CJ
 */
interface MultiplatformExt {
    /**
     * 在构建完毕后，将 npm 产出复制到 node_module 目录
     * @param moduleName 模块名称(npm)
     */
    fun copyNpmPackageToNodeAfter(moduleName: String, taskName: String = "build")
}