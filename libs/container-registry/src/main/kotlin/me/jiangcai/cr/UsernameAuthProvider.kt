package me.jiangcai.cr

import java.util.*

/**
 * @author CJ
 */
class UsernameAuthProvider(
    private val username: String,
    private val password: String,
) : AuthProvider {
    override val ownerName: String
        get() = username

    override fun parametersForTokenAuth(): Map<String, String>? = null

    override fun headersForTokenAuth(): Map<String, String> = mapOf(
        "Authorization" to "Basic ${
            Base64.getEncoder()
                .encodeToString(("$username:$password").encodeToByteArray())
        }"
    )
}