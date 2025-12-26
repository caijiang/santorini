@file:OptIn(ExperimentalSerializationApi::class)

package me.jiangcai.cr

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonNames

/**
 * @author CJ
 */
@Serializable
@JsonIgnoreUnknownKeys
data class TokenResponse(
    val token: String? = null,
    @JsonNames("access_token")
    val accessToken: String? = null,
) {
    val readToken: String
        get() = token ?: accessToken ?: throw IllegalArgumentException("$this 啥也不是")
}
