@file:OptIn(ExperimentalUuidApi::class)

package io.santorini.console

import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.santorini.LoginUserData
import io.santorini.consoleModuleEntry
import io.santorini.kubernetes.*
import io.santorini.model.PageResult
import io.santorini.model.ServiceType
import io.santorini.schema.EnvData
import io.santorini.schema.ServiceMetaData
import io.santorini.schema.UserData
import io.santorini.test.mockUserModule
import io.santorini.tools.addServiceMeta
import io.santorini.tools.createStandardClient
import io.santorini.tools.database
import org.junit.jupiter.api.AfterAll
import org.testcontainers.containers.MySQLContainer
import java.util.*
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi

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
        val kubernetesClient = mockk<KubernetesClient>()
        every { kubernetesClient.namespace } returns "ns"

        application {
            consoleModuleEntry(database = mysql.database, kubernetesClient = kubernetesClient)
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
        val pod = mockk<Pod>(relaxed = true)

//        mockkStatic(KubernetesClient::findClusterRole)
//        mockkStatic(KubernetesClient::currentPod)
        mockkStatic(Pod::rootOwner)
//        mockkStatic(KubernetesClient::clusterRoleBindingBySubject)
        mockkStatic("io.santorini.kubernetes.RoleKt")
        every {
            kubernetesClient.currentPod()
        } returns pod
        every { pod.rootOwner() } returns mockk()
        val envRole = ClusterRoleBuilder()
            .withNewMetadata()
            .withName(UUID.randomUUID().toString())
            .endMetadata()
            .build()
        every {
            kubernetesClient.findClusterRole(any(), "env-${envId}-visitable", anyNullable())
        } returns envRole

        every {
            kubernetesClient.clusterRoleBindingBySubject(anyNullable())
        } returns emptyList()

        withClue("一个普通用户，最早无法操作任何环境") {
            manager.get("https://localhost/users/${userData.id}/envs").apply {
                status shouldBe HttpStatusCode.OK
                body<List<String>>() shouldHaveSize 0
            }
        }

//        mockkStatic(KubernetesClient::assignClusterRole)
        every {
            kubernetesClient.assignClusterRole(anyNullable(), anyNullable())
        } answers {

        }
        manager.post("https://localhost/users/${userData.id}/envs") {
            contentType(ContentType.Application.Json)
            setBody(envId)
        }.apply {
            status shouldBe HttpStatusCode.Created
        }
//        verify(exactly = 1) {
//            kubernetesClient.assignClusterRole(envRole.metadata.name, any())
//        }
        every {
            kubernetesClient.clusterRoleBindingBySubject(anyNullable())
        } returns listOf(envRole.metadata.name)

        withClue("经过管理员授权即可查看") {
            manager.get("https://localhost/users/${userData.id}/envs").apply {
                status shouldBe HttpStatusCode.OK
                body<List<String>>() shouldBe listOf(envId)
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
            id = "role-play-service", name = "UserRoleKtTest", type = ServiceType.JVM, requirements = null
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
//            manager.get("https://localhost/users/${userData.id}/services").apply {
//                status shouldBe HttpStatusCode.OK
//                body<List<String>>() shouldBe listOf(envId)
//            }
        }

    }
}