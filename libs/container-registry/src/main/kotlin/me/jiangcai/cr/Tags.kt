@file:OptIn(ExperimentalSerializationApi::class)

package me.jiangcai.cr

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * @author CJ
 */
@Serializable
@JsonIgnoreUnknownKeys
data class Tags(
    val name: String,
    val tags: List<String>
)
