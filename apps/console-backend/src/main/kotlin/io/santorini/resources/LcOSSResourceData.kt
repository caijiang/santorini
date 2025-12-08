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
data class LcOSSResourceData(
    val endpoint: String,
    @SerialName("access-key")
    val accessKey: String,
    @SerialName("secret-key")
    val secretKey: String,
    val region: String,
    @SerialName("private-bucket")
    val privateBucket: String,
    @SerialName("public-bucket")
    val publicBucket: String,
    @SerialName("private-domain")
    val privateDomain: String,
    @SerialName("public-domain")
    val publicDomain: String,
    val dir: String,
)
