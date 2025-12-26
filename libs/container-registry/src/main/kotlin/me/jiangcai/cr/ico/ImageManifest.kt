@file:OptIn(ExperimentalSerializationApi::class)

package me.jiangcai.cr.ico

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import me.jiangcai.cr.Deployable
import me.jiangcai.cr.Platform

/**
 * @author CJ
 */
@Serializable
@JsonIgnoreUnknownKeys
data class ImageManifest(
    val digest: String,
    val platform: Platform,
    val size: Long
) : Deployable {
    override val architecture: String
        get() = platform.architecture
    override val os: String
        get() = platform.os
}
