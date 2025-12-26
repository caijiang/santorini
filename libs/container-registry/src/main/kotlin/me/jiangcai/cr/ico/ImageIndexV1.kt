@file:OptIn(ExperimentalSerializationApi::class)

package me.jiangcai.cr.ico

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * [规格](https://github.com/opencontainers/image-spec/blob/v1.0/image-index.md)
 * @author CJ
 */
@Serializable
@JsonIgnoreUnknownKeys
data class OciImageIndexV1(
    val manifests: List<ImageManifest>
)