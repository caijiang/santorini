@file:Suppress("OPT_IN_USAGE")

package me.jiangcai.cr

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import java.util.*

/**
 * @author CJ
 */
@Serializable
@JsonIgnoreUnknownKeys
data class DockerAuthsConfig(
    val auths: Map<String, DockerAuth>
)

@Serializable
@JsonIgnoreUnknownKeys
data class DockerAuth(
    val username: String? = null,
    val password: String? = null,
    val auth: String? = null,
) {
    fun toAuthProvider(name: String): AuthProvider {
        return object : AuthProvider {
            override val ownerName: String
                get() = name

            override fun parametersForTokenAuth(): Map<String, String>? = null

            override fun headersForTokenAuth(): Map<String, String> =
                mapOf(
                    "Authorization" to "Basic ${
                        auth ?: Base64.getEncoder()
                            .encodeToString(("$username:$password").encodeToByteArray())
                    }"
                )
        }
    }
}