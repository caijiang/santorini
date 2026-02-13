package io.santorini.io.santorini.test

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * @author CJ
 */
@JsonIgnoreUnknownKeys
@Serializable
data class LocalFeishuConfig(
    val demoUserOpenId: String,
    val id: String,
    val secret: String,
)
