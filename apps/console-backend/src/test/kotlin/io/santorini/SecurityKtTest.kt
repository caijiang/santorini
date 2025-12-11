@file:Suppress("TestFunctionName", "NonAsciiCharacters")

package io.santorini

import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.santorini.test.mockUserModule
import io.santorini.tools.createStandardClient
import kotlin.test.Test

/**
 * @author CJ
 */
class SecurityKtTest {
    @Test
    fun 获取当前身份信息() {
        testApplication {
            application {
                consoleModule()
                mockUserModule()
            }

            val client = createStandardClient()

            client.get("/mockUser").apply {
                status shouldBe HttpStatusCode.OK
            }
            client.get("https://localhost/currentLogin").apply {
                status shouldBe HttpStatusCode.OK
                contentType()?.match(ContentType.Application.Json) shouldBe true
            }

        }
    }
}
