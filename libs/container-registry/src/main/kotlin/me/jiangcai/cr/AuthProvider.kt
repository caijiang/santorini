package me.jiangcai.cr

/**
 * @author CJ
 */
interface AuthProvider {
    val ownerName: String

    /**
     * [参考](https://distribution.github.io/distribution/spec/auth/token/)
     */
    fun parametersForTokenAuth(): Map<String, String>?

    /**
     * [参考](https://distribution.github.io/distribution/spec/auth/token/)
     * 提供类似`Authorization: Basic base64(username:password)`的头信息即可
     */
    fun headersForTokenAuth(): Map<String, String>?
    // OAuth2
}