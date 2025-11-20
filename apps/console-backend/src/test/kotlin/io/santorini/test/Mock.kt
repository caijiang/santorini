package io.santorini.io.santorini.test

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.InSiteUserData
import io.santorini.OAuthPlatform
import io.santorini.OAuthPlatformUserDataAuditResult
import io.santorini.saveUserData


private val logger = KotlinLogging.logger {}
fun Application.mockUserModule() {
    routing {
        get("/mockUser/{audit?}") {
            logger.info { "视图伪装成用户: ${call.pathParameters["audit"]}" }
            call.saveUserData(
                InSiteUserData(
                    call.pathParameters["audit"]?.let { OAuthPlatformUserDataAuditResult.valueOf(it) }
                        ?: OAuthPlatformUserDataAuditResult.User,
                    OAuthPlatform.Feishu,
                    "1",
                    "测试人物",
                    "https://abc.com",
                    "AGT",
                    "SA1"
                )
            )
            call.respond(HttpStatusCode.OK)
        }
    }
}
