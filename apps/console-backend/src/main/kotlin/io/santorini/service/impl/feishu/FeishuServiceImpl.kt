package io.santorini.service.impl.feishu

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.santorini.service.FeishuService
import io.santorini.service.KubernetesClientService
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * token 保存在哪里呢？secret!
 * @author CJ
 */
class FeishuServiceImpl(
    private val kubernetesClientService: KubernetesClientService,
    private val httpClient: HttpClient,
    private val id: String = System.getenv("FEISHU_APP_ID") ?: "notwork",
    private val secret: String = System.getenv("FEISHU_APP_SECRET") ?: "notwork",
) : FeishuService {
    private var currentToken: FeishuToken? = null

    /**
     * https://open.feishu.cn/document/server-docs/im-v1/message/create
     */
    override suspend fun sendSingleMessage(openId: String, post: FeishuPost) {
        val token = queryToken()
        val result = httpClient.post("https://open.feishu.cn/open-apis/im/v1/messages") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            parameter("receive_id_type", "open_id")
            setBody(
                mapOf(
                    "receive_id" to openId,
                    "msg_type" to "post",
                    "content" to jacksonObjectMapper().writeValueAsString(post.asJacksonRoot())
                )
            )
        }.body<FeishuCreateMessageResult>()

        if (result.code != 0) {
            throw IllegalStateException("飞书发送消息时响应业务错误:$result")
        }
    }

    @JsonIgnoreUnknownKeys
    @Serializable
    private data class FeishuCreateMessageResult(
        val code: Int,
        @SerialName("msg")
        val message: String?,
    )

    /**
     * https://open.feishu.cn/document/server-docs/authentication-management/access-token/tenant_access_token_internal
     * 1. 本地的 token
     * 2. 查看 kubernetes 保存的
     * 3. 去请求，并且保存到 k8s
     */
    private suspend fun queryToken(): String {
        if (currentToken?.workInNext(10.minutes) == true) {
            return currentToken?.token!!
        }
        // 查看远端 token
        val t = kubernetesClientService.queryFeishuToken(id)
        if (t?.workInNext(10.minutes) == true) {
            currentToken = t
            return t.token
        }
        // 请求吧
        val requestClock = Clock.System.now()
        val response = httpClient.post("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    mapOf("app_id" to id, "app_secret" to secret)
                )
            )
        }
        val result = response.body<FeishuTenantAccessTokenResult>()
        if (result.code != 0) {
            throw IllegalStateException("飞书响应业务错误:$result")
        }
        if (result.token == null || result.expire == null) {
            throw IllegalStateException("飞书响应非法:$result")
        }
        val newToken = FeishuToken(
            result.token,
            if (result.expire <= 0) requestClock.plus(1.days).epochSeconds else requestClock.plus(result.expire.seconds).epochSeconds
        )

        currentToken = newToken
        kubernetesClientService.saveFeishuToken(id, newToken)
        return newToken.token
    }

    @JsonIgnoreUnknownKeys
    @Serializable
    private data class FeishuTenantAccessTokenResult(
        val code: Int,
        @SerialName("msg")
        val message: String?,
        @SerialName("tenant_access_token")
        val token: String?,
        val expire: Int?
    )
}