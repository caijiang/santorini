package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.console.schema.HostData
import io.santorini.kubernetes.model.ClusterResourceStat
import io.santorini.model.ResourceType
import io.santorini.model.ServiceRole
import io.santorini.service.KubernetesClientService
import io.santorini.service.impl.feishu.FeishuToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * @author CJ
 */
class KubernetesClientServiceImpl(override val kubernetesClient: KubernetesClient) : KubernetesClientService {
    private val logger = KotlinLogging.logger {}
    override suspend fun currentPodRootOwner(): HasMetadata {
        return withContext(Dispatchers.IO) {
            kubernetesClient.currentPod().rootOwner(kubernetesClient)
        }
    }

    override fun findResourcesInNamespace(namespace: String, type: ResourceType?): List<SantoriniResource> {
        return kubernetesClient.findResourcesInNamespace(namespace, type)
    }

    override fun createEnvResourceInPlain(namespace: String, data: Map<String, String>, labels: Map<String, String>) {
        kubernetesClient.createEnvResourceInPlain(namespace, data, labels)
    }

    override fun createEnvResourceInSecret(namespace: String, data: Map<String, String>, labels: Map<String, String>) {
        kubernetesClient.createEnvResourceInSecret(namespace, data, labels)
    }

    override fun removeResource(namespace: String, name: String) {
        kubernetesClient.removeResource(namespace, name)
    }

    override suspend fun clusterResourceStat(): ClusterResourceStat {
        return withContext(Dispatchers.IO) {
            kubernetesClient.clusterResourceStat()
        }
    }

    override fun removeAllServiceRolesFromNamespace(root: HasMetadata, serviceAccountName: String, namespace: String) =
        kubernetesClient.removeAllServiceRolesFromNamespace(root, serviceAccountName, namespace)

    override fun makesureRightEnvRoles(
        root: HasMetadata,
        serviceAccountName: String,
        namespace: String,
        withIngress: Boolean
    ) =
        kubernetesClient.makesureRightEnvRoles(root, serviceAccountName, namespace, withIngress)

    override fun makesureRightServiceRoles(
        root: HasMetadata,
        serviceAccountName: String,
        namespace: String,
        serviceRoles: Map<String, List<ServiceRole>>
    ) = kubernetesClient.makesureRightServiceRoles(root, serviceAccountName, namespace, serviceRoles)

    override fun queryFeishuToken(id: String): FeishuToken? {
        val secret = kubernetesClient.secrets().inNamespace(kubernetesClient.namespace)
            .withName("feishu-access-token-${id.safeInK8s()}")
            .get()
        if (secret == null) return null

        return try {
            val decoded = secret.data.mapValues { (_, v) ->
                String(Base64.getDecoder().decode(v))
            }
            FeishuToken(
                decoded["token"] as String,
                decoded["expiration"]!!.toLong(),
            )
        } catch (e: Exception) {
            logger.error(e) { "Error while fetching token" }
            null
        }
    }

    override fun saveFeishuToken(id: String, token: FeishuToken) {
        kubernetesClient.secrets().resource(
            SecretBuilder()
                .withNewMetadata()
                .withNamespace(kubernetesClient.namespace)
                .withName("feishu-access-token-${id.safeInK8s()}")
                .endMetadata()
                .withStringData<String, String>(
                    mapOf(
                        "token" to token.token,
                        "expiration" to token.expireTimeSeconds.toString()
                    )
                )
                .build()
        ).serverSideApply()
    }

    override fun readIngressHostFromNamespace(namespace: String): List<HostData> {
        val x = kubernetesClient
            .network()
            .v1()
            .ingresses()
            .inNamespace(namespace)
            .list()

        val x1 = x.items.flatMap { ingress ->
            val issuerName = ingress.metadata?.annotations?.get("cert-manager.io/cluster-issuer")
            ingress.spec.rules.map { rule ->
                val hostname = rule.host
                val secretName = ingress.spec?.tls?.find {
                    it.hosts.contains(hostname)
                }?.secretName

                HostData(hostname, issuerName, secretName).cleanShot()
            }
        }
            // host 必须有效
            .filter {
                it.hostname.isNotBlank()
            }
            // 支持没有证书，但不支持 有签名但是没证书
            .filter {
                if (it.issuerName == null)
                    true
                else it.secretName != null
            }

        logger.debug {
            "经过去重过滤前: $x1"
        }
        val names = x1.map { it.hostname }.distinct()

        return names.map { name ->
            val mc = x1.filter {
                it.hostname == name
            }
            if (mc.size > 1) {
                logger.warn {
                    "在${namespace}流量入口:${name}存在多个:${mc}"
                }
            }
            mc[0]
        }
    }
}

private fun String.safeInK8s(): String {
    return this.replace("[^a-zA-Z0-9]".toRegex(), "")
}
