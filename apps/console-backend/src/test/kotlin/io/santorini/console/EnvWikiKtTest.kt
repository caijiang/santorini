package io.santorini.console

import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import io.santorini.console.schema.EnvWikiContent
import io.santorini.console.schema.EnvWikiService
import io.santorini.console.schema.UserRoleService
import io.santorini.well.StatusException
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level
import kotlin.test.Test

/**
 * @author CJ
 */
class EnvWikiKtTest {

    /**
     * 保留下来是 它可能优化整体测试结构
     */
    @Test
    fun testWiki2() = testApplication {
        val envWikiService = mockk<EnvWikiService>()
        val userRoleService = mockk<UserRoleService>()
        application {
            install(Koin) {
                slf4jLogger()
                modules(module {
                    single {
                        userRoleService
                    }
                    single {
                        envWikiService
                    }
                })
            }
            install(StatusPages) {
                exception<StatusException> { call, cause ->
                    call.respond(cause.status)
                }
            }
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json(Json)
            }
            install(CallLogging) {
                level = Level.INFO
//        logger = ktLogger
            }
            install(Resources)
            configureConsoleEnvWiki()
        }

        client.put("/envs/abc/wikis/name") {

        }.apply {
            status shouldBe HttpStatusCode.InternalServerError
        }

        coEvery {
            envWikiService.listByEnv("ei", title = "ti")
        } returns listOf()
        client.get("/publicEnvWikis/ei/ti") {

        }.apply {
            status shouldBe HttpStatusCode.NotFound
        }

        coEvery {
            envWikiService.listByEnv("ei", title = "ti")
        } returns listOf(EnvWikiContent(title = "", content = "", global = false))
        client.get("/publicEnvWikis/ei/ti") {

        }.apply {
            status shouldBe HttpStatusCode.Forbidden
        }

        coEvery {
            envWikiService.listByEnv("ei", title = "ti")
        } returns listOf(EnvWikiContent(title = "", content = "", global = true))
        client.get("/publicEnvWikis/ei/ti") {

        }.apply {
            status shouldBe HttpStatusCode.OK
        }

    }

}