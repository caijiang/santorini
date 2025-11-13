package io.santorini.feishu

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
data class UserInfo(
    val name: String,
    @SerialName("avatar_big")
    val avatarUrl: String,
    @SerialName("open_id")
    val openId: String,
    @SerialName("user_id")
    val userId: String,
)
