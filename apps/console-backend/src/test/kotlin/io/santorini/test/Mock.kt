@file:OptIn(ExperimentalUuidApi::class)

package io.santorini.test

import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.mockk.every
import io.mockk.mockk
import io.santorini.OAuthPlatform
import io.santorini.OAuthPlatformUserData
import io.santorini.OAuthPlatformUserDataAuditResult
import io.santorini.console.schema.ComputeResourceCpu
import io.santorini.console.schema.ComputeResourceMemory
import io.santorini.console.schema.ComputeResources
import io.santorini.console.schema.UserRoleService
import io.santorini.saveUserData
import me.jiangcai.cr.Deployable
import org.koin.ktor.ext.inject
import java.util.*
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}
fun Application.mockUserModule() {
    val userService = inject<UserRoleService>().value
    routing {
        get("/mockUser/{audit?}") {
            logger.info { "视图伪装成用户: ${call.pathParameters["audit"]}" }

            val sa = mockServiceAccount()

            // 这里是模拟飞书授权登录的场景
            val user = userService.oAuthPlatformUserDataToSiteUserData(
                mockOAuthPlatformUserData(),
                call.pathParameters["audit"]?.let { OAuthPlatformUserDataAuditResult.valueOf(it) }
                    ?: OAuthPlatformUserDataAuditResult.User,
                sa
            )
            call.saveUserData(
                user.copy(platformAccessToken = "AGT")
            )
            call.respond(HttpStatusCode.OK)
        }
    }
}

fun mockOAuthPlatformUserData() = object : OAuthPlatformUserData {
    override val platform: OAuthPlatform
        get() = OAuthPlatform.Feishu
    override val stablePk: String
        get() = UUID.randomUUID().toString().replace("-", "").substring(0, 8)
    override val name: String
        get() = "测试" + UUID.randomUUID().toString().replace("-", "").substring(0, 4)
    override val avatarUrl: String
        get() = "https://abc.com"
}

fun mockServiceAccount(): ServiceAccount = ServiceAccountBuilder()
    .withNewMetadata()
    .withName(UUID.randomUUID().toString().replace("-", "").substring(0, 8))
    .endMetadata()
    .build()

fun mockThatConfigMapNameWill(
    mockKubernetesClient: KubernetesClient,
    namespace: String,
    name: String,
    configMap: ConfigMap? = null,
) {
    val gettable = mockk<Resource<ConfigMap>>()
    every { gettable.get() } returns configMap

    val nameable = mockk<NonNamespaceOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>>>()
    every {
        nameable.withName(name)
    } returns gettable

    val configmaps = mockk<MixedOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>>>()
    every { configmaps.inNamespace(namespace) } returns nameable

    every { mockKubernetesClient.configMaps() } returns configmaps
}

fun mockThatSecretNameWill(
    mockKubernetesClient: KubernetesClient,
    namespace: String,
    name: String,
    secret: Secret? = null,
) {
    val gettable = mockk<Resource<Secret>>()
    every { gettable.get() } returns secret

    val nameable = mockk<NonNamespaceOperation<Secret, SecretList, Resource<Secret>>>()
    every {
        nameable.withName(name)
    } returns gettable

    val configmaps = mockk<MixedOperation<Secret, SecretList, Resource<Secret>>>()
    every { configmaps.inNamespace(namespace) } returns nameable

    every { mockKubernetesClient.secrets() } returns configmaps
}

/**
 * 其实更好的做法是模拟响应的 node
 * 确保 [io.santorini.console.schema.DeploymentService.preDeploy] 工作顺利
 */
fun mockDeploymentServicePreDeployWorkFineWith(
    mockKubernetesClient: KubernetesClient,
    imageInfo: Pair<String, List<Deployable>>
) {
    val first = imageInfo.second.first()
    val nodeSystemInfo = mockk<NodeSystemInfo>(relaxed = true)
    every {
        nodeSystemInfo.operatingSystem
    } returns first.os
    every {
        nodeSystemInfo.architecture
    } returns first.architecture

    val nodeStatus = mockk<NodeStatus>(relaxed = true)
    every {
        nodeStatus.nodeInfo
    } returns nodeSystemInfo

    val node = mockk<Node>(relaxed = true)
    every {
        node.status
    } returns nodeStatus

    val nodeList = mockk<NodeList>(relaxed = true)
    every {
        nodeList.items
    } returns listOf(node)

    val x = mockk<NonNamespaceOperation<Node, NodeList, Resource<Node>>>()
    every {
        x.list()
    } returns nodeList

    every { mockKubernetesClient.nodes() } returns x
}

fun mockComputeResources(): ComputeResources {
    return ComputeResources(
        ComputeResourceCpu(
            100, 200
        ),
        ComputeResourceMemory(
            128, 1024
        )
    )
}