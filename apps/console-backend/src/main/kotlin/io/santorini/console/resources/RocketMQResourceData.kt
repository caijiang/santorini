package io.santorini.console.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * @author CJ
 */
@Serializable
@JsonIgnoreUnknownKeys
data class RocketMQResourceData(
    @SerialName("name-server")
    val nameServer: String,
    @SerialName("access-key")
    val accessKey: String? = null,
    @SerialName("secret-key")
    val secretKey: String? = null,
)
