@file:OptIn(ExperimentalUuidApi::class)

package io.santorini.console

import io.fabric8.kubernetes.client.KubernetesClient
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.santorini.consoleModuleEntry
import io.santorini.kubernetes.SantoriniResourceKubernetesImpl
import io.santorini.kubernetes.applyStringSecret
import io.santorini.kubernetes.findResourcesInNamespace
import io.santorini.model.Lifecycle
import io.santorini.model.ResourceRequirement
import io.santorini.model.ResourceType
import io.santorini.model.ServiceType
import io.santorini.schema.DeploymentDeployData
import io.santorini.schema.EnvData
import io.santorini.schema.ServiceMetaData
import io.santorini.schema.mergeJson
import io.santorini.test.mockUserModule
import io.santorini.tools.createStandardClient
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * @author CJ
 */
class DeploymentKtTest {
    @Test
    fun testRoot() = testApplication {
        val kubernetesClient = mockk<KubernetesClient>()
        application {
            consoleModuleEntry(kubernetesClient = kubernetesClient)
            mockUserModule()
        }

        val c = createStandardClient()

        c.get("/mockUser/Manager").apply {
            status shouldBe HttpStatusCode.OK
        }

        val deployDemoService = ServiceMetaData(
            id = "demo-for-deploy-service", name = "范例", type = ServiceType.JVM,
            requirements = listOf(
                ResourceRequirement(ResourceType.Mysql)
            ),
            lifecycle = Lifecycle(),
        )
        c.post("https://localhost/services") {
            contentType(ContentType.Application.Json)
            setBody(
                mergeJson(
                    deployDemoService, """
  {
    "resources":{
     "cpu": {
      "requestMillis":100,
      "limitMillis":1000
     }
    }
  }
"""
                )
            )
        }.apply {
            status shouldBe HttpStatusCode.OK
        }
        val deployTargetEnv = EnvData(id = "deploy", name = "test", production = true)

        withClue("一开始可以查得出来，但是带上 env就查不出来了。") {
            c.get("https://localhost/services?keyword=${deployDemoService.id}").apply {
                status shouldBe HttpStatusCode.OK
                body<List<ServiceMetaData>>() shouldHaveSize 1
            }
            c.get("https://localhost/services?keyword=${deployDemoService.id}&envId=${deployTargetEnv.id}").apply {
                status shouldBe HttpStatusCode.OK
                body<List<ServiceMetaData>>() shouldHaveSize 0
            }
        }

        c.post("https://localhost/envs") {
            contentType(ContentType.Application.Json)
            setBody(deployTargetEnv)
        }.apply {
            status shouldBe HttpStatusCode.OK
        }

        // 要求没有满足，那不行的
        c.post("https://localhost/deployments/deploy/${deployTargetEnv.id}/${deployDemoService.id}") {
            contentType(ContentType.Application.Json)
        }.apply {
            status shouldBe HttpStatusCode.UnsupportedMediaType
        }

        val deployData = DeploymentDeployData(
            imageRepository = "image-repository",
        )

        c.post("https://localhost/deployments/deploy/${deployTargetEnv.id}/${deployDemoService.id}") {
            contentType(ContentType.Application.Json)
            setBody(deployData)
        }.apply {
            withClue("缺乏了必要的资源数据") {
                status shouldBe HttpStatusCode.BadRequest
            }
        }

        c.post("https://localhost/deployments/deploy/${deployTargetEnv.id}/${deployDemoService.id}") {
            contentType(ContentType.Application.Json)
            setBody(
                deployData.copy(
                    resourcesSupply = mapOf(
                        ResourceRequirement(ResourceType.Mysql, "hello").toString() to "never"
                    )
                )
            )
        }.apply {
            withClue("多了无需的数据") {
                status shouldBe HttpStatusCode.BadRequest
            }
        }

        mockkStatic(KubernetesClient::findResourcesInNamespace)
        every {
            kubernetesClient.findResourcesInNamespace(deployTargetEnv.id!!, anyNullable())
        } returns listOf()

        c.post("https://localhost/deployments/deploy/${deployTargetEnv.id}/${deployDemoService.id}") {
            contentType(ContentType.Application.Json)
            setBody(
                deployData.copy(
                    resourcesSupply = mapOf(
                        ResourceRequirement(ResourceType.Mysql).toString() to "never"
                    )
                )
            )
        }.apply {
            withClue("资源不存在") {
                status shouldBe HttpStatusCode.BadRequest
            }
        }

        // mock 一个 mysql 资源

        every {
            kubernetesClient.findResourcesInNamespace(deployTargetEnv.id!!, anyNullable())
        } returns listOf(
            SantoriniResourceKubernetesImpl(
                ResourceType.Mysql, "never", null, mapOf(), mapOf(
                    "username" to "username",
                    "password" to "password",
                    "host" to "host",
                    "port" to "port",
                    "database" to "database"
                )
            )
        )

        mockkStatic(KubernetesClient::applyStringSecret)
        every {
            kubernetesClient.applyStringSecret(deployTargetEnv.id!!, any(), any())
        } answers {

        }
        c.post("https://localhost/deployments/deploy/${deployTargetEnv.id}/${deployDemoService.id}") {
            contentType(ContentType.Application.Json)
            setBody(
                deployData.copy(
                    resourcesSupply = mapOf(
                        ResourceRequirement(ResourceType.Mysql).toString() to "never"
                    )
                )
            )
        }.apply {
            withClue("正常工作了") {
                status shouldBe HttpStatusCode.OK
                val deploymentId = body<Uuid>()
                deploymentId.shouldNotBeNull()

                c.put("https://localhost/deployments/$deploymentId/targetResourceVersion") {
                    contentType(ContentType.Application.Json)
                    setBody(deploymentId.toString())
                }.apply {
                    status shouldBe HttpStatusCode.NoContent
                }

                withClue("提交过后是不可以删除的") {
                    c.delete("https://localhost/deployments/$deploymentId")
                        .apply {
                            status shouldBe HttpStatusCode.BadRequest
                        }
                }

            }
        }

        c.get("https://localhost/services/${deployDemoService.id}/lastRelease/${deployTargetEnv.id}").apply {
            status shouldBe HttpStatusCode.OK
            val deploymentDeployData = body<DeploymentDeployData>()
            deploymentDeployData.targetResourceVersion.shouldNotBeNull()
            deploymentDeployData.serviceDataSnapshot.shouldNotBeNull()
            deploymentDeployData.copy(
                targetResourceVersion = null,
                serviceDataSnapshot = null
            ) shouldBe deployData.copy(
                resourcesSupply = mapOf(
                    ResourceRequirement(ResourceType.Mysql).toString() to "never"
                )
            )
        }

        withClue("部署完成了，就可以查出来了。") {
            c.get("https://localhost/services?keyword=${deployDemoService.id}").apply {
                status shouldBe HttpStatusCode.OK
                body<List<ServiceMetaData>>() shouldHaveSize 1
            }
            c.get("https://localhost/services?keyword=${deployDemoService.id}&envId=${deployTargetEnv.id}").apply {
                status shouldBe HttpStatusCode.OK
                body<List<ServiceMetaData>>() shouldHaveSize 1
            }
        }

    }
}