package io.santorini.console.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * @author CJ
 */
@Serializable
@JsonIgnoreUnknownKeys
data class NacosAuthResourceData(
    @SerialName("server-addr")
    val serverAddr: String,
    val username: String? = null,
    val password: String? = null,
    @SerialName("access-key")
    val accessKey: String? = null,
    @SerialName("secret-key")
    val secretKey: String? = null,
)

@Serializable
@JsonIgnoreUnknownKeys
data class NacosNamespaceResourceData(
    val namespace: String
)
