@file:Suppress("TestFunctionName", "NonAsciiCharacters")

package io.santorini.console

import io.fabric8.kubernetes.client.KubernetesClient
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import io.santorini.console.schema.EnvData
import io.santorini.consoleModuleEntry
import io.santorini.kubernetes.createEnvResourceInPlain
import io.santorini.kubernetes.createEnvResourceInSecret
import io.santorini.kubernetes.updateOne
import io.santorini.model.ResourceType
import io.santorini.service.KubernetesClientService
import io.santorini.test.mockThatConfigMapNameWill
import io.santorini.test.mockThatSecretNameWill
import io.santorini.test.mockUserModule
import io.santorini.tools.createStandardClient
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @author CJ
 */
class EnvKtTest {

    @Test
    fun 环境以及环境资源测试() = testApplication {
        val kubernetesClient = mockk<KubernetesClient>(relaxed = true)
        val clientService = mockk<KubernetesClientService>(relaxed = true)
        every { clientService.kubernetesClient } returns kubernetesClient
        application {
            consoleModuleEntry(kubernetesClient = kubernetesClient, kubernetesClientService = clientService)
            mockUserModule()
        }

        val c = createStandardClient()

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
        c.get("https://localhost/envs").apply {
            status shouldBe HttpStatusCode.OK
            body<List<EnvData>>().shouldContain(
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

        every {
            clientService.findResourcesInNamespace(id, anyNullable())
        } returns listOf()
        c.get("https://localhost/envs/$id/resources").apply {
            status shouldBe HttpStatusCode.OK
            withClue("现在是空的资源") {
                body<List<String>>() shouldHaveSize 0
            }
        }
        verify(exactly = 1) {
            clientService.findResourcesInNamespace(id, null)
        }
        c.get("https://localhost/envs/$id/resources?type=${ResourceType.Mysql}").apply {
            status shouldBe HttpStatusCode.OK
            withClue("现在是空的资源") {
                body<List<String>>() shouldHaveSize 0
            }
        }
        verify(exactly = 1) {
            clientService.findResourcesInNamespace(id, ResourceType.Mysql)
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

        mockkStatic(KubernetesClient::createEnvResourceInSecret)
        mockkStatic(KubernetesClient::createEnvResourceInPlain)
        every {
            kubernetesClient.createEnvResourceInPlain(any(), any(), any())
        } answers {}
        every {
            kubernetesClient.createEnvResourceInSecret(any(), any(), any())
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
            clientService.createEnvResourceInSecret(
                id, mapOf(
                    "password" to resourceData.properties["password"]!!,
                ), mapOf(
                    "santorini.io/manageable" to "true",
                    "santorini.io/id" to resourceData.name,
                    "santorini.io/resource-type" to resourceData.type.name,
                    "santorini.io/description" to resourceData.description!!,
                )
            )
        }

        verify(exactly = 1) {
            clientService.createEnvResourceInPlain(
                id, mapOf(
                    "username" to "username",
                    "host" to "host",
                    "port" to "port",
                    "database" to "database",
                ), mapOf(
                    "santorini.io/manageable" to "true",
                    "santorini.io/id" to resourceData.name,
                    "santorini.io/resource-type" to resourceData.type.name,
                    "santorini.io/description" to resourceData.description!!,
                )
            )
        }

        every {
            clientService.removeResource(any(), any())
        } answers {}
        c.delete("https://localhost/envs/$id/resources/${resourceData.name}").apply {
            status shouldBe HttpStatusCode.OK
        }
        verify(exactly = 1) {
            clientService.removeResource(
                id, resourceData.name,
            )
        }

        //<editor-fold desc="namespace共享环境变量">
        println("开始测试namespace共享环境变量")
        mockThatSecretNameWill(kubernetesClient, id, Share_Env_Config_Name)
        mockThatConfigMapNameWill(kubernetesClient, id, Share_Env_Config_Name)
        c.get("https://localhost/envs/$id/shareEnvs").apply {
            status shouldBe HttpStatusCode.OK
            body<List<String>>() shouldHaveSize 0
        }
        val envName = "OPP"
        mockkStatic(KubernetesClient::updateOne)
        every {
            kubernetesClient.updateOne(any(), any(), any(), any(), any())
        } answers {}
        c.put("https://localhost/envs/$id/shareEnvs/$envName") {
            contentType(ContentType.Application.Json)
            setBody("welcome")
        }.apply {
            status shouldBe HttpStatusCode.NoContent
        }
        verify(exactly = 1) {
            kubernetesClient.updateOne(id, Share_Env_Config_Name, false, envName, "welcome")
        }
        //</editor-fold>
    }

}