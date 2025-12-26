@file:OptIn(ExperimentalSerializationApi::class)

package me.jiangcai.cr

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * ```json
 * "architecture": "amd64",
 * "os": "linux"
 * ```
 * @author CJ
 */
@Serializable
@JsonIgnoreUnknownKeys
data class Platform(
    val architecture: String,
    val os: String,
)
