@file:OptIn(ExperimentalSerializationApi::class)

package me.jiangcai.cr.docker

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import me.jiangcai.cr.Deployable

/**
 * [规格](https://github.com/opencontainers/image-spec/blob/main/config.md)
 * @author CJ
 */
@Serializable
@JsonIgnoreUnknownKeys
data class ContainerImageV1(
    override val architecture: String,
    override val os: String,
) : Deployable
