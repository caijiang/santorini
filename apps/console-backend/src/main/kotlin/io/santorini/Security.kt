package io.santorini

import common.LoginUser
import io.fabric8.kubernetes.client.KubernetesClient
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.santorini.kubernetes.findOrCreateServiceAccount
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


fun Application.configureSecurity(
    httpClient: HttpClient,
    kubernetesClient: KubernetesClient,
    audit: OAuthPlatformUserDataAudit
) {
    install(ContentNegotiation) {
        json(Json)
    }
    install(Sessions) {
//        cookie<UserSession>("USER_SESSION") {
//            cookie.extensions["SameSite"] = "lax"
//        }
        cookie<String>("U1") {
            cookie.extensions["SameSite"] = "lax"
            cookie.secure = true
        }
    }
    authentication {
        oauth("auth-oauth-feishu") {
            client = httpClient
            // 是我方的回调地址 必须写到对方后台去
            urlProvider = { System.getenv("FEISHU_CALLBACK_URL") }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "feishu",
                    authorizeUrl = "https://accounts.feishu.cn/open-apis/authen/v1/authorize",
                    accessTokenUrl = "https://open.feishu.cn/open-apis/authen/v2/oauth/token",
                    requestMethod = HttpMethod.Post,
                    clientId = System.getenv("FEISHU_APP_ID"),
                    clientSecret = System.getenv("FEISHU_APP_SECRET")
                )
            }
        }
        oauth("auth-oauth-google") {
            urlProvider = { "http://localhost:8080/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = System.getenv("GOOGLE_CLIENT_ID"),
                    clientSecret = System.getenv("GOOGLE_CLIENT_SECRET"),
                    defaultScopes = listOf("https://www.googleapis.com/auth/userinfo.profile")
                )
            }
            client = httpClient
        }
    }
    routing {
        // 获取当前身份, 按 everest 规则进行
        get("currentLogin") {
// 401
            val user = call.queryUserData()
            log.debug("Getting user data from data: {}", user)
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(LoginUserData(user.name, user.avatarUrl, arrayOf()))
            }
        }
        authenticate("auth-oauth-feishu") {
            get("loginFeishu") {
                call.respondRedirect("/callbackFeishu")
            }

            get("/callbackFeishu") {
                val principal: OAuthAccessTokenResponse.OAuth2 = call.authentication.principal()!!
                // 获取用户详情
                // Parameters [token_type=[Bearer], access_token=[..], expires_in=[7200], scope=[auth:user.id:read], code=[0]]
                val platformUserData = principal.fetchData(OAuthPlatform.Feishu, httpClient)
                val result = audit.audit(platformUserData)
                if (result == null) {
                    call.respondRedirect("/loginFailed")
                    return@get
                }
                // 资源名称 不可以超过 63  前置就用了 20 就剩下 43了
                //         "open_id": "ou-caecc734c2e3328a62489fe0648c4b98779515d3",
                // 准入检测， 据此 在内部建立与之对应的用户
                // 寻找用户 如果已经找到了 那就算了
                // 不用担心异常  https://ktor.io/docs/server-status-pages.html 可以处理的
                val account = kubernetesClient.findOrCreateServiceAccount(platformUserData, result)
                //
                call.saveUserData(
                    InSiteUserData(
                        platformUserData.platform,
                        platformUserData.stablePk,
                        platformUserData.name,
                        platformUserData.avatarUrl,
                        principal.accessToken,
                        account.metadata.name
                    )
                )
                call.respondRedirect("/")
            }
        }
        authenticate("auth-oauth-google") {
            get("login") {
                call.respondRedirect("/callback")
            }

            get("/callback") {
                val principal: OAuthAccessTokenResponse.OAuth2? = call.authentication.principal()
//                call.sessions.set(UserSession(principal?.accessToken.toString()))
                call.respondRedirect("/hello")
            }
        }
    }
}

/**
 * 站内用户信息，不应当暴露
 */
@Serializable
data class InSiteUserData(
    val platform: OAuthPlatform,
    /**
     * 稳定的 id
     */
    val stablePk: String,
    val name: String,
    val avatarUrl: String,
    /**
     * 认证中心的 token
     * 妈耶，其他已设计好的地方居然不是这个
     */
    val platformAccessToken: String,
    val serviceAccountName: String,
)

@Serializable
data class LoginUserData(
    /**
     * 名称
     */
    override val name: String,

    /**
     * 头像 url
     */
    override val avatarUrl: String?,

    override val grantAuthorities: Array<String>
) : LoginUser {
    override val bytesId: ByteArray?
        get() = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LoginUserData) return false

        if (name != other.name) return false
        if (avatarUrl != other.avatarUrl) return false
        if (!grantAuthorities.contentEquals(other.grantAuthorities)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (avatarUrl?.hashCode() ?: 0)
        result = 31 * result + grantAuthorities.contentHashCode()
        return result
    }
}