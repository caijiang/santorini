package io.santorini

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.santorini.feishu.ResponseBody
import io.santorini.feishu.UserInfo
import java.security.MessageDigest
import java.util.*

//对外访问的 auth
private val log = KotlinLogging.logger {}

/**
 * 认证平台
 */
enum class OAuthPlatform {
    /**
     * 飞书应用,
     * 我们就可以用用户组织关系判断了
     */
    Feishu
}

/**
 * 认证平台结果
 */
interface OAuthPlatformUserData {
    val platform: OAuthPlatform

    /**
     * 稳定的 id
     */
    val stablePk: String
    val name: String
    val avatarUrl: String

    @Suppress("unused")
    val hashId: String
        get() {
            val input = platform.name + stablePk
            val sha256 = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            val b32 = Base64.getEncoder().encodeToString(sha256)
                .replace("=", "")          // 去掉 '='
                .replace("+", "a")         // 替换不安全字符
                .replace("/", "b")         // 替换不安全字符
                .lowercase()
            if (b32.length < 40) return b32
            return b32.substring(0, 40)
        }
}


data class FeishuOAuthPlatformUserData(
    override val platform: OAuthPlatform,
    override val stablePk: String,
    override val name: String,
    override val avatarUrl: String
) : OAuthPlatformUserData

/**
 * 从对应平台获取更为详细的用户信息
 */
suspend fun OAuthAccessTokenResponse.OAuth2.fetchData(
    platform: OAuthPlatform,
    client: HttpClient
): OAuthPlatformUserData {
    if (platform == OAuthPlatform.Feishu) {
//        https://open.feishu.cn/document/server-docs/authentication-management/login-state-management/get?appId=cli_a65cc60f00ee900c
        // https://open.feishu.cn/document/server-docs/contact-v3/user/get?appId=cli_a65cc60f00ee900c
        // https://open.feishu.cn/document/server-docs/contact-v3/group/member_belong
        log.info { "Fetching OAuth Platform.${platform.name};$tokenType $accessToken" }
        return client.get {
            method = HttpMethod.Get
            url("https://open.feishu.cn/open-apis/authen/v1/user_info")
            headers {
                append("Authorization", "$tokenType $accessToken")
                append("Content-Type", "application/json; charset=utf-8")
            }
        }.body<ResponseBody<UserInfo>>().makeSureSuccessWithResult().let {
            FeishuOAuthPlatformUserData(platform, it.openId, it.name, it.avatarUrl)
        }

    } else throw NotImplementedError("并不支持 $platform")

}

// 我们有个介入器，告知我们 1: 可以登录么 2: 是超管么？