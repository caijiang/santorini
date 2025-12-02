package io.santorini.console

import io.fabric8.kubernetes.client.KubernetesClient
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import io.santorini.consoleModuleEntry
import io.santorini.io.santorini.test.mockUserModule
import io.santorini.kubernetes.applyStringConfig
import io.santorini.kubernetes.applyStringSecret
import io.santorini.kubernetes.deleteConfigMapAndSecret
import io.santorini.kubernetes.findResourcesInNamespace
import io.santorini.model.ResourceType
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
        val kubernetesClient = mockk<KubernetesClient>(relaxed = true)
        application {
            consoleModuleEntry(kubernetesClient = kubernetesClient)
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

        mockkStatic(KubernetesClient::findResourcesInNamespace)
        every {
            kubernetesClient.findResourcesInNamespace(id, anyNullable())
        } returns listOf()
        c.get("https://localhost/envs/$id/resources").apply {
            status shouldBe HttpStatusCode.OK
            withClue("现在是空的资源") {
                body<List<String>>() shouldHaveSize 0
            }
        }
        verify(exactly = 1) {
            kubernetesClient.findResourcesInNamespace(id, null)
        }
        c.get("https://localhost/envs/$id/resources?type=${ResourceType.Mysql}").apply {
            status shouldBe HttpStatusCode.OK
            withClue("现在是空的资源") {
                body<List<String>>() shouldHaveSize 0
            }
        }
        verify(exactly = 1) {
            kubernetesClient.findResourcesInNamespace(id, ResourceType.Mysql)
        }
        c.post("https://localhost/envs/$id/resources") {
            contentType(ContentType.Application.Json)
            setBody(
                SantoriniResourceData(
                    ResourceType.Mysql, "test", "example", mapOf(
                        "username" to "username",
                    )
                )
            )
        }.apply {
            withClue("缺乏必要数据") {
                status shouldBe HttpStatusCode.BadRequest
            }
        }

        mockkStatic(KubernetesClient::applyStringSecret)
        mockkStatic(KubernetesClient::applyStringConfig)
        every {
            kubernetesClient.applyStringConfig(any(), any(), any(), any())
        } answers {}
        every {
            kubernetesClient.applyStringSecret(any(), any(), any(), any())
        } answers {}

        val resourceData = SantoriniResourceData(
            ResourceType.Mysql, "test", "example", mapOf(
                "username" to "username",
                "password" to "password",
                "host" to "host",
                "port" to "port",
                "database" to "database",
            )
        )

        c.post("https://localhost/envs/$id/resources") {
            contentType(ContentType.Application.Json)
            setBody(
                resourceData
            )
        }.apply {
            status shouldBe HttpStatusCode.OK
        }

        verify(exactly = 1) {
            kubernetesClient.applyStringSecret(
                id, resourceData.name, mapOf(
                    "password" to resourceData.properties["password"]!!,
                ), mapOf(
                    "santorini.io/manageable" to "true",
                    "santorini.io/resource-type" to resourceData.type.name,
                    "santorini.io/description" to resourceData.description!!,
                )
            )
        }

        verify(exactly = 1) {
            kubernetesClient.applyStringConfig(
                id, resourceData.name, mapOf(
                    "username" to "username",
                    "host" to "host",
                    "port" to "port",
                    "database" to "database",
                ), mapOf(
                    "santorini.io/manageable" to "true",
                    "santorini.io/resource-type" to resourceData.type.name,
                    "santorini.io/description" to resourceData.description!!,
                )
            )
        }

        mockkStatic(KubernetesClient::deleteConfigMapAndSecret)
        every {
            kubernetesClient.deleteConfigMapAndSecret(any(), any())
        } answers {}
        c.delete("https://localhost/envs/$id/resources/${resourceData.name}").apply {
            status shouldBe HttpStatusCode.OK
        }
        verify(exactly = 1) {
            kubernetesClient.deleteConfigMapAndSecret(
                id, resourceData.name,
            )
        }
    }

}