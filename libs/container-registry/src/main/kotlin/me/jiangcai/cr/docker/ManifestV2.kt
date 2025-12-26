@file:OptIn(ExperimentalSerializationApi::class)

package me.jiangcai.cr.docker

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * https://distribution.github.io/distribution/spec/manifest-v2-2/#image-manifest-field-descriptions
 * @author CJ
 */
@Serializable
@JsonIgnoreUnknownKeys
data class ManifestV2(
    val config: ResourceConfig? = null,
)


@Serializable
@JsonIgnoreUnknownKeys
data class ResourceConfig(
    val mediaType: String,
    val digest: String,
    val size: Long
)
