package io.santorini.console

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotHaveSize
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.santorini.LoginUserData
import io.santorini.console.model.PageResult
import io.santorini.console.schema.EnvData
import io.santorini.console.schema.ServiceMetaData
import io.santorini.console.schema.UserData
import io.santorini.consoleModuleEntry
import io.santorini.io.santorini.test.MockJobService
import io.santorini.model.Lifecycle
import io.santorini.model.ServiceRole
import io.santorini.model.ServiceType
import io.santorini.service.KubernetesClientService
import io.santorini.test.mockUserModule
import io.santorini.tools.addServiceMeta
import io.santorini.tools.createStandardClient
import io.santorini.tools.database
import org.junit.jupiter.api.AfterAll
import org.testcontainers.containers.MySQLContainer
import java.util.*
import kotlin.test.Test

/**
 * @author CJ
 */
class UserRoleKtTest {

    companion object {
        private val mysql = MySQLContainer("mysql:8.0").apply { start() }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            mysql.stop()
        }
    }

    @Test
    fun userAndRole() = testApplication {
        val kubernetesClient = mockk<KubernetesClientService>()

        application {
            consoleModuleEntry(
                database = mysql.database,
                kubernetesClientService = kubernetesClient,
                scheduleJobServiceLoader = { _, _ -> MockJobService })
            mockUserModule()
        }

        val manager = createStandardClient()

        manager.get("/mockUser/Manager").apply {
            status shouldBe HttpStatusCode.OK
        }
        // 管理员天生具备 user,

        withClue("这个时候还没任何普通用户") {
            manager.get("https://localhost/users").apply {
                status shouldBe HttpStatusCode.OK
                val list = body<List<UserData>>()
                list shouldHaveSize 0
            }
            manager.get("https://localhost/users?limit=10").apply {
                status shouldBe HttpStatusCode.OK
                body<PageResult<UserData>>().total shouldBe 0
            }
        }

        val envId = UUID.randomUUID().toString().replace("-", "")
        // 先处理简单的 - 环境 可见性
        manager.post("https://localhost/envs") {
            contentType(ContentType.Application.Json)
            setBody(EnvData(id = envId, name = "test", production = true))
        }.apply {
            status shouldBe HttpStatusCode.OK
        }
        // 分配环境; ClusterRole -> 生成的名字 -> santorini.io/role: env-id-visitable -> visitableClusterRoleName (每次都会检查,存在就行，没有就再度创建 )
        // 分配服务 serviceRole 服务角色 目前提供 Owner
        // 这个动作什么时候实施？
        // 查询所属环境
        val user = createStandardClient()
        user.get("/mockUser").apply {
            status shouldBe HttpStatusCode.OK
        }
        val userData = user.get("https://localhost/currentLogin").body<LoginUserData>()
        every {
            kubernetesClient.currentPodRootOwner()
        } returns mockk()

        withClue("一个普通用户，最早无法操作任何环境") {
            manager.get("https://localhost/users/${userData.id}/envs").apply {
                status shouldBe HttpStatusCode.OK
                body<List<String>>() shouldHaveSize 0
            }
        }
        every { kubernetesClient.removeAllServiceRolesFromNamespace(any(), anyNullable(), anyNullable()) } answers {}
        every { kubernetesClient.makesureRightServiceRoles(any(), any(), any(), any()) } answers {}
        every { kubernetesClient.makesureRightEnvRoles(any(), any(), any(), false) } answers {}
        manager.post("https://localhost/users/${userData.id}/envs") {
            contentType(ContentType.Application.Json)
            setBody(envId)
        }.apply {
            status shouldBe HttpStatusCode.Created
        }

        withClue("授权了环境需要调用 makesureRightEnvRoles") {
            verify(exactly = 0) {
                kubernetesClient.removeAllServiceRolesFromNamespace(any(), any(), envId)
            }
            verify(exactly = 1) {
                kubernetesClient.makesureRightEnvRoles(any(), any(), envId, false)
            }
        }

        withClue("经过管理员授权即可查看") {
            manager.get("https://localhost/users/${userData.id}/envs").apply {
                status shouldBe HttpStatusCode.OK
                body<List<String>>() shouldBe listOf(envId)
            }
            user.get("https://localhost/envs").apply {
                status shouldBe HttpStatusCode.OK
                body<List<EnvData>>().map { it.id } shouldBe listOf(envId)
            }
        }

        withClue("但只能查看授权过的那个") {
            manager.post("https://localhost/envs") {
                contentType(ContentType.Application.Json)
                setBody(EnvData(id = envId + "2", name = "test", production = true))
            }.apply {
                status shouldBe HttpStatusCode.OK
            }

            manager.get("https://localhost/users/${userData.id}/envs").apply {
                status shouldBe HttpStatusCode.OK
                body<List<String>>() shouldBe listOf(envId)
            }
            user.get("https://localhost/envs").apply {
                status shouldBe HttpStatusCode.OK
                body<List<EnvData>>().map { it.id } shouldBe listOf(envId)
            }
        }

        withClue("现在管理员可以看到普通用户") {
            manager.get("https://localhost/users").apply {
                status shouldBe HttpStatusCode.OK
                val list = body<List<UserData>>()
                list shouldNotHaveSize 0
            }
            manager.get("https://localhost/users?limit=10").apply {
                status shouldBe HttpStatusCode.OK
                body<PageResult<UserData>>().total shouldNotBe 0
            }
        }

        // 新增一个服务
        val rolePlayService = ServiceMetaData(
            id = "role-play-service", name = "UserRoleKtTest", type = ServiceType.JVM, requirements = null,
            lifecycle = Lifecycle(),
        )
        manager.addServiceMeta(rolePlayService)

        withClue("普通用户，没有服务可看") {
            manager.get("https://localhost/services").apply {
                status shouldBe HttpStatusCode.OK
                body<List<ServiceMetaData>>() shouldNotHaveSize 0
            }
            user.get("https://localhost/services").apply {
                status shouldBe HttpStatusCode.OK
                body<List<ServiceMetaData>>() shouldHaveSize 0
            }
            manager.get("https://localhost/users/${userData.id}/services").apply {
                status shouldBe HttpStatusCode.OK
                body<Map<String, List<ServiceRole>>>() shouldHaveSize 0
            }
            manager.post("https://localhost/users/${userData.id}/services") {
                contentType(ContentType.Application.Json)
                setBody(rolePlayService.id to ServiceRole.Owner)
            }.apply {
                status shouldBe HttpStatusCode.NoContent
            }

            manager.get("https://localhost/users/${userData.id}/services").apply {
                status shouldBe HttpStatusCode.OK
                body<Map<String, List<ServiceRole>>>() shouldHaveSize 1 shouldContain (rolePlayService.id to listOf(
                    ServiceRole.Owner
                ))
            }

            user.get("https://localhost/services").apply {
                status shouldBe HttpStatusCode.OK
                body<List<ServiceMetaData>>() shouldHaveSize 1
            }
        }

        withClue("授权之后需要调用各种 api") {
            verify(exactly = 0) {
                kubernetesClient.removeAllServiceRolesFromNamespace(any(), any(), envId)
            }
            verify(exactly = 1) {
                kubernetesClient.makesureRightServiceRoles(
                    any(), any(), envId, mapOf(
                        rolePlayService.id to listOf(
                            ServiceRole.Owner
                        )
                    )
                )
            }
        }

        withClue("允许设置权限--ingress") {
            manager.put("https://localhost/users/${userData.id}/grantAuthorities/ingress") {
                contentType(ContentType.Application.Json)
                setBody(true)
            }.apply {
                status shouldBe HttpStatusCode.NoContent
            }

            manager.get("https://localhost/users?limit=40").apply {
                status shouldBe HttpStatusCode.OK
                val page = body<PageResult<UserData>>()
                page.records.find {
                    it.id == userData.id.toString()
                }?.shouldNotBeNull()
                page.records.find {
                    it.id == userData.id.toString()
                }?.grantAuthorities?.ingress shouldBe true
            }
        }

        withClue("移除环境后") {
            manager.delete("https://localhost/users/${userData.id}/envs/$envId").apply {
                status shouldBe HttpStatusCode.NoContent
            }
            withClue("授权之后需要调用各种 api") {
                verify(exactly = 1) {
                    kubernetesClient.removeAllServiceRolesFromNamespace(any(), any(), envId)
                }
            }
        }

    }
}