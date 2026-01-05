@file:OptIn(ExperimentalUuidApi::class)

package io.santorini.console

import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.santorini.LoginUserData
import io.santorini.console.schema.*
import io.santorini.consoleModuleEntry
import io.santorini.kubernetes.*
import io.santorini.model.*
import io.santorini.service.ImageService
import io.santorini.test.mockComputeResources
import io.santorini.test.mockDeploymentServicePreDeployWorkFineWith
import io.santorini.test.mockUserModule
import io.santorini.tools.createStandardClient
import me.jiangcai.cr.Deployable
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * @author CJ
 */
@Suppress("NonAsciiCharacters", "TestFunctionName")
class DeploymentKtTest {
    @Test
    fun 部署服务() = testApplication {
        val kubernetesClient = mockk<KubernetesClient>()
        val imageService = mockk<ImageService>()
        application {
            consoleModuleEntry(kubernetesClient = kubernetesClient, imageServiceLoader = {
                imageService
            })
            mockUserModule()
        }

        val manager = createStandardClient()

        manager.get("/mockUser/Manager").apply {
            status shouldBe HttpStatusCode.OK
        }

        val deployDemoService = ServiceMetaData(
            id = "demo-for-deploy-service", name = "范例", type = ServiceType.JVM,
            requirements = listOf(
                ResourceRequirement(ResourceType.Mysql)
            ),
            lifecycle = Lifecycle(),
        )
        manager.post("https://localhost/services") {
            contentType(ContentType.Application.Json)
            setBody(
                mergeJson(
                    deployDemoService, """
  {
  }
"""
                )
            )
        }.apply {
            status shouldBe HttpStatusCode.OK
        }
        val deployTargetEnv = EnvData(id = "deploy", name = "test", production = true)

        withClue("一开始可以查得出来，但是带上 env就查不出来了。") {
            manager.get("https://localhost/services?keyword=${deployDemoService.id}").apply {
                status shouldBe HttpStatusCode.OK
                body<List<ServiceMetaData>>() shouldHaveSize 1
            }
            manager.get("https://localhost/services?keyword=${deployDemoService.id}&envId=${deployTargetEnv.id}")
                .apply {
                    status shouldBe HttpStatusCode.OK
                    body<List<ServiceMetaData>>() shouldHaveSize 0
                }
        }

        manager.post("https://localhost/envs") {
            contentType(ContentType.Application.Json)
            setBody(deployTargetEnv)
        }.apply {
            status shouldBe HttpStatusCode.OK
        }

        // 要求没有满足，那不行的
        manager.post("https://localhost/deployments/deploy/${deployTargetEnv.id}/${deployDemoService.id}") {
            contentType(ContentType.Application.Json)
        }.apply {
            status shouldBe HttpStatusCode.UnsupportedMediaType
        }

        val deployData = DeploymentDeployData(
            imageRepository = "image-repository",
            resources = mockComputeResources(),
        )

        manager.post("https://localhost/deployments/deploy/${deployTargetEnv.id}/${deployDemoService.id}") {
            contentType(ContentType.Application.Json)
            setBody(deployData)
        }.apply {
            withClue("缺乏了必要的资源数据") {
                status shouldBe HttpStatusCode.BadRequest
            }
        }

        manager.post("https://localhost/deployments/deploy/${deployTargetEnv.id}/${deployDemoService.id}") {
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

        manager.post("https://localhost/deployments/deploy/${deployTargetEnv.id}/${deployDemoService.id}") {
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
        val imageInfo = "sha256..." to listOf(
            object : Deployable {
                override val architecture: String
                    get() = "a"
                override val os: String
                    get() = "o"
            }
        )
        coEvery { imageService.toImageInfo(any(), any()) } returns imageInfo
        mockDeploymentServicePreDeployWorkFineWith(kubernetesClient, imageInfo)
        manager.post("https://localhost/deployments/preDeploy/${deployTargetEnv.id}/${deployDemoService.id}") {
            contentType(ContentType.Application.Json)
            setBody(
                deployData.copy(
                    resourcesSupply = mapOf(
                        ResourceRequirement(ResourceType.Mysql).toString() to "never"
                    )
                )
            )
        }.apply {
            status shouldBe HttpStatusCode.OK
            val result = body<PreDeployResult>()
            result.imageDigest shouldBe "sha256..."
            result.warnMessage.shouldBeNull()
            result.imagePlatformMatch shouldBe true
            result.successfulEnvs.shouldNotBeNull()
        }

        manager.post("https://localhost/deployments/deploy/${deployTargetEnv.id}/${deployDemoService.id}") {
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

                manager.put("https://localhost/deployments/$deploymentId/targetGeneration") {
                    contentType(ContentType.Application.Json)
                    setBody(1010101L.toString())
                }.apply {
                    status shouldBe HttpStatusCode.NoContent
                }

                withClue("提交过后是不可以删除的") {
                    manager.delete("https://localhost/deployments/$deploymentId")
                        .apply {
                            status shouldBe HttpStatusCode.BadRequest
                        }
                }

            }
        }

        manager.get("https://localhost/services/${deployDemoService.id}/lastRelease/${deployTargetEnv.id}")
            .apply {
                status shouldBe HttpStatusCode.OK
                val deploymentDeployData = body<DeploymentDeployData>()
                deploymentDeployData.targetGeneration.shouldNotBeNull()
                deploymentDeployData.serviceDataSnapshot.shouldNotBeNull()
                deploymentDeployData.copy(
                    targetGeneration = null,
                    serviceDataSnapshot = null
                ) shouldBe deployData.copy(
                    resourcesSupply = mapOf(
                        ResourceRequirement(ResourceType.Mysql).toString() to "never"
                    )
                )
            }

        withClue("部署完成了，就可以查出来了。") {
            manager.get("https://localhost/services?keyword=${deployDemoService.id}").apply {
                status shouldBe HttpStatusCode.OK
                body<List<ServiceMetaData>>() shouldHaveSize 1
            }
            manager.get("https://localhost/services?keyword=${deployDemoService.id}&envId=${deployTargetEnv.id}")
                .apply {
                    status shouldBe HttpStatusCode.OK
                    body<List<ServiceMetaData>>() shouldHaveSize 1
                }
        }

        withClue("管理员可以按需查看发布记录") {
            manager.get("https://localhost/deployments?serviceId=${deployDemoService.id}&envId=${deployTargetEnv.id}")
                .apply {
                    status shouldBe HttpStatusCode.OK
                    val list = body<List<DeploymentDataInList>>()
                    list shouldHaveSize 1
                }
        }
        withClue("普通人看不到，但授权后就可以看到了") {
            val user = createStandardClient()
            user.get("/mockUser").apply {
                status shouldBe HttpStatusCode.OK
            }
            val userData = user.get("https://localhost/currentLogin").body<LoginUserData>()
            user.get("https://localhost/deployments?serviceId=${deployDemoService.id}&envId=${deployTargetEnv.id}")
                .apply {
                    status shouldBe HttpStatusCode.OK
                    val list = body<List<DeploymentDataInList>>()
                    list shouldHaveSize 0
                }

            mockkStatic(kubernetesClient::removeAllServiceRolesFromNamespace)
            mockkStatic(kubernetesClient::makesureRightServiceRoles)
            mockkStatic(kubernetesClient::makesureRightEnvRoles)

            every {
                kubernetesClient.removeAllServiceRolesFromNamespace(
                    any(),
                    anyNullable(),
                    anyNullable()
                )
            } answers {}
            every { kubernetesClient.makesureRightServiceRoles(any(), any(), any(), any()) } answers {}
            every { kubernetesClient.makesureRightEnvRoles(anyNullable(), any(), any()) } answers {}

            val pod = mockk<Pod>(relaxed = true)

            mockkStatic(Pod::rootOwner)
            mockkStatic(KubernetesClient::currentPod)
//            mockkStatic("io.santorini.kubernetes.RoleKt")
            every {
                kubernetesClient.currentPod()
            } returns pod
            every { pod.rootOwner() } returns mockk()

            manager.post("https://localhost/users/${userData.id}/envs") {
                contentType(ContentType.Application.Json)
                setBody(deployTargetEnv.id)
            }.apply {
                status shouldBe HttpStatusCode.Created
            }

            user.get("https://localhost/deployments?serviceId=${deployDemoService.id}&envId=${deployTargetEnv.id}")
                .apply {
                    status shouldBe HttpStatusCode.OK
                    val list = body<List<DeploymentDataInList>>()
                    list shouldHaveSize 0
                }

            manager.post("https://localhost/users/${userData.id}/services") {
                contentType(ContentType.Application.Json)
                setBody(deployDemoService.id to ServiceRole.Owner)
            }.apply {
                status shouldBe HttpStatusCode.NoContent
            }

            user.get("https://localhost/deployments?serviceId=${deployDemoService.id}&envId=${deployTargetEnv.id}")
                .apply {
                    status shouldBe HttpStatusCode.OK
                    val list = body<List<DeploymentDataInList>>()
                    list shouldHaveSize 1
                }

        }

    }
}