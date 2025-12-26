package me.jiangcai.cr

/**
 * @author CJ
 */
data class Image(
    private val registry: String = "docker.io",
    private val name: String,
    val tag: String = "latest",
) {
    /**
     * 实际 api 链接的主机
     */
    val registryHost: String
        get() = if (registry == "docker.io") "registry-1.docker.io" else registry
    val visitName: String
        get() = if (registry == "docker.io") dockerIoName(name) else name

    private fun dockerIoName(name: String): String {
        if (name.contains("/")) return name
        return "library/$name"
    }

    companion object {
        fun ofStringAndTag(input: String, tag: String = "latest"): Image {
            // 按 / 分开 看看第一个是不是 url
            val list = input.split('/')
            if (list[0].contains(".")) {
                return Image(
                    list[0], list.drop(1).joinToString("/"), tag
                )
            }
            return Image(name = input, tag = tag)
        }

        fun ofString(input: String): Image {
            // 有没有分号
            if (input.contains(":")) {
                val s1 = input.split(":")
                if (s1.size != 2) throw IllegalArgumentException("无法识别:$input")
                val f1 = s1[0]
                val f2 = s1[1]
                return ofStringAndTag(f1, f2)
            }
            return ofStringAndTag(input)
        }
    }
}
