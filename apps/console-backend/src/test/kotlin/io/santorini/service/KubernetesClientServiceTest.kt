package io.santorini.io.santorini.service

import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.santorini.demoPlatformUserData
import io.santorini.io.santorini.test.LocalK8sClusterConfig
import io.santorini.kubernetes.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.Test

private val json = Json {
    ignoreUnknownKeys = true
}

/**
 * 默认 /var/run/secrets/kubernetes.io/serviceaccount/token
 */
fun workWithLocalKubernetesCluster(javaClass: Class<Any>, block: KubernetesClient.() -> Unit) {
    javaClass.getResourceAsStream("/local-build-k8s-cluster-config.json")?.let { inputStream ->
        val data = inputStream.use {
            json.decodeFromStream<LocalK8sClusterConfig>(it)
        }
// release-name-santorini-santorini-console-backend-758976479wsqcs
        val portWork = try {
            ServerSocket(data.port).use {
                it.reuseAddress = true
                it.bind(InetSocketAddress(data.port))
            }
            false
        } catch (e: IOException) {
            true
        }

        if (!portWork) {
            println("本地集群依赖端口:${data.port}但是没有发现，跳过执行")
            return
        }

        KubernetesClientBuilder().withConfig(
            ConfigBuilder()
                .withNamespace("kube-santorini")
                .withMasterUrl("https://localhost:${data.port}/")
                .withTrustCerts(true)
                .withAutoOAuthToken(data.autoOAuthToken)
                .withAdditionalProperties<String, String>(
                    mapOf(
                        CONFIG_ADDITIONAL_KEY_HOSTNAME to data.podName
                    )
                )
                .build()
        ).build().apply(block)
    }
}

fun KubernetesClient.demoUserServiceAccount(): String = findOrCreateServiceAccount(
    demoPlatformUserData.platform.name,
    demoPlatformUserData.stablePk,
    false
).metadata.name

/**
 * 只有支持测试的 k8s集群对本地开放了端口，此事才存在可行性。
 * @author CJ
 */
class KubernetesClientServiceTest {

    @Test
    fun removeAllServiceRolesFromNamespace() {
        val testNamespace = "test-ns"
        workWithLocalKubernetesCluster(javaClass) {
            val service = KubernetesClientServiceImpl(this)
            val sa = demoUserServiceAccount()
            // 第一步添加
            val root = currentPod().rootOwner(this)

            service.makesureRightEnvRoles(root, sa, testNamespace, true)
            withClue("ingress需要存在 2 个 binding") {
                rbac().roleBindings()
                    .inNamespace(testNamespace)
                    .withLabel("santorini.io/type", "env-role")
                    .list().items.filter { roleBinding -> roleBinding.subjects.any { it.name == sa } } shouldHaveSize 2
            }

            service.removeAllServiceRolesFromNamespace(root, sa, testNamespace)
            service.makesureRightEnvRoles(root, sa, testNamespace, false)
            withClue("正常的话1 个 binding") {
                rbac().roleBindings()
                    .inNamespace(testNamespace)
                    .withLabel("santorini.io/type", "env-role")
                    .list().items.filter { roleBinding -> roleBinding.subjects.any { it.name == sa } } shouldHaveSize 1
            }

            service.removeAllServiceRolesFromNamespace(root, sa, testNamespace)
            // 这个时候 获取它的角色

            withClue("确保干净的清理") {
                rbac().roleBindings()
                    .inNamespace(testNamespace)
                    .withLabel("santorini.io/type", "env-role")
                    .list().items.filter { roleBinding -> roleBinding.subjects.any { it.name == sa } } shouldHaveSize 0
            }

        }
    }
}