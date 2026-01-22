package io.santorini.io.santorini.test

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * @author CJ
 */
@JsonIgnoreUnknownKeys
@Serializable
data class LocalK8sClusterConfig(
    val podName: String,
    val port: Int,
    val autoOAuthToken: String
)
