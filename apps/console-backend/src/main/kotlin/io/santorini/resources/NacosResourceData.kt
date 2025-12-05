package io.santorini.resources

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * @author CJ
 */
@OptIn(ExperimentalSerializationApi::class)
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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class NacosNamespaceResourceData(
    val namespace: String
)
