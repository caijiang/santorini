package io.santorini.console

import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.santorini.consoleModule
import io.santorini.schema.HostData
import io.santorini.test.mockUserModule
import io.santorini.tools.createStandardClient
import kotlin.test.Test

private val logger = KotlinLogging.logger {}

/**
 * @author CJ
 */
class HostKtTest {
    @Test
    fun testHost() = testApplication {
        application {
            consoleModule()
            mockUserModule()
        }

        val c = createStandardClient()

        c.get("/mockUser/Manager").apply {
            status shouldBe HttpStatusCode.OK
        }

        val demoHost = HostData("h1", "v1", "v2")
        c.post("https://localhost/hosts") {
            contentType(ContentType.Application.Json)
            setBody(demoHost)
        }.apply {
            status shouldBe HttpStatusCode.OK
        }
        c.post("https://localhost/hosts") {
            contentType(ContentType.Application.Json)
            setBody(demoHost)
        }.apply {
            status shouldBe HttpStatusCode.BadRequest
        }

        logger.info {
            "成功同步来一次"
        }

        val demoHost2 = HostData("h2", "v3", "v4")
        c.post("https://localhost/hosts/sync") {
            contentType(ContentType.Application.Json)
            setBody(listOf(demoHost, demoHost2))
        }.apply {
            status shouldBe HttpStatusCode.OK
            body<Int>() shouldBe 1
        }

        c.post("https://localhost/hosts/sync") {
            contentType(ContentType.Application.Json)
            setBody(listOf(demoHost.copy(issuerName = "vvv"), demoHost2))
        }.apply {
            status shouldBe HttpStatusCode.BadRequest
        }

    }
}