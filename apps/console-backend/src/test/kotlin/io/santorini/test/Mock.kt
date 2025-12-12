@file:OptIn(ExperimentalUuidApi::class)

package io.santorini.test

import io.fabric8.kubernetes.api.model.ServiceAccountBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.OAuthPlatform
import io.santorini.OAuthPlatformUserData
import io.santorini.OAuthPlatformUserDataAuditResult
import io.santorini.saveUserData
import io.santorini.schema.UserRoleService
import org.koin.ktor.ext.inject
import java.util.*
import kotlin.uuid.ExperimentalUuidApi


private val logger = KotlinLogging.logger {}
fun Application.mockUserModule() {
    val userService = inject<UserRoleService>().value
    routing {
        get("/mockUser/{audit?}") {
            logger.info { "视图伪装成用户: ${call.pathParameters["audit"]}" }

            val sa = ServiceAccountBuilder()
                .withNewMetadata()
                .withName(UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .endMetadata()
                .build()

            // 这里是模拟飞书授权登录的场景
            val user = userService.oAuthPlatformUserDataToSiteUserData(
                object : OAuthPlatformUserData {
                    override val platform: OAuthPlatform
                        get() = OAuthPlatform.Feishu
                    override val stablePk: String
                        get() = UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                    override val name: String
                        get() = "测试" + UUID.randomUUID().toString().replace("-", "").substring(0, 4)
                    override val avatarUrl: String
                        get() = "https://abc.com"
                }, call.pathParameters["audit"]?.let { OAuthPlatformUserDataAuditResult.valueOf(it) }
                    ?: OAuthPlatformUserDataAuditResult.User, sa
            )
            call.saveUserData(
                user.copy(platformAccessToken = "AGT")
            )
            call.respond(HttpStatusCode.OK)
        }
    }
}
