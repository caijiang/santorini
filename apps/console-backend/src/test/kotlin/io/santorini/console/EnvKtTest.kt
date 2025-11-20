package io.santorini.console

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.santorini.consoleModule
import io.santorini.io.santorini.test.mockUserModule
import io.santorini.schema.EnvData
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @author CJ
 */
class EnvKtTest {

    @Test
    fun testRoot() = testApplication {
        application {
            consoleModule()
            mockUserModule()
        }

        val c = createClient {
            install(ContentNegotiation) {
                json(Json)
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.HEADERS
            }
            install(HttpCookies) {
                storage = AcceptAllCookiesStorage()
            }
        }

        c.get("/mockUser/Manager").apply {
            status shouldBe HttpStatusCode.OK
        }

        c.post("https://localhost/envs") {
            contentType(ContentType.Application.Json)
//            this.setBody(ExposedEnv("??", "", false))
        }.apply {
            assertEquals(HttpStatusCode.UnsupportedMediaType, status)
        }

        val id = "abc"
        c.post("https://localhost/envs") {
            contentType(ContentType.Application.Json)
            setBody(EnvData(id = id, name = "test", production = true))
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        c.get("https://localhost/envs/batch/$id,foo,bar").apply {
            assertEquals(HttpStatusCode.OK, status)
            body<List<EnvData>>().shouldContainOnly(
                EnvData(id = id, name = "test", production = true),
            )
        }

        c.patch("https://localhost/envs/$id") {
            contentType(ContentType.Application.Json)
            setBody(EnvData(name = "test2", production = true))
//            this.setBody(ExposedEnv("??", "", false))
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        c.get("https://localhost/envs/batch/$id,foo,bar").apply {
            assertEquals(HttpStatusCode.OK, status)
            withClue("已经修改过了，不再是原来那个了") {
                body<List<EnvData>>().shouldContainOnly(
                    EnvData(id = id, name = "test2", production = true),
                )
            }
        }
    }

}