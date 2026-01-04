package io.santorini.feishu

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * @author CJ
 */
@Serializable
@JsonIgnoreUnknownKeys
data class ResponseBody<T>(
    val code: Int,
    @SerialName("msg")
    val message: String? = null,
    val data: T? = null
) {
    @Suppress("unused")
    fun makeSureSuccess() {
        if (!success)
            throw Exception("$code: $message")
    }

    fun makeSureSuccessWithResult(): T {
        if (!success)
            throw Exception("$code: $message")
        return data ?: throw Exception("$code: $message 但没有相应")
    }

    private val success: Boolean
        get() = code == 0
}
