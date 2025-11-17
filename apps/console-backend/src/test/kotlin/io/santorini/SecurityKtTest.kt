@file:Suppress("TestFunctionName", "NonAsciiCharacters")

package io.santorini

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

private val log = KotlinLogging.logger {}

/**
 * @author CJ
 */
class SecurityKtTest {
    @Test
    fun 获取当前身份信息() {
        testApplication {
            application {
                consoleModule()
                myModule()
            }

            val cookies = client.get("/writeUser").apply {
                assertEquals(HttpStatusCode.OK, status)
            }.let {
                val sc = it.headers["Set-Cookie"]
                log.info {
                    sc
                }
                assertNotNull(sc)
                // 分析 SetCookie
                sc!!.split("; ").first().split("=")
            }
            client.get("/currentLogin") {
                log.info { "设置 cookie: $cookies" }
                log.info { "设置 cookie: ${cookies.last().decodeURLQueryComponent()}" }
                cookie(cookies.first(), cookies.last().decodeURLQueryComponent())
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
                val ct = contentType()
                assertNotNull(ct)
                assertTrue(ct!!.match(ContentType.Application.Json))
            }

        }
    }
}

private fun Application.myModule() {
    routing {
        get("/writeUser") {
            call.saveUserData(
                InSiteUserData(
                    OAuthPlatform.Feishu,
                    "1",
                    "测试人物",
                    "https://abc.com",
                    "AGT",
                    "SA1"
                )
            )
            call.respondText("Hello World!")
        }
    }
}
