package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.networking.v1.Ingress
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.kubernetes.model.ClusterResourceStat
import io.santorini.kubernetes.model.HostData
import io.santorini.model.ResourceType
import io.santorini.model.ServiceRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @author CJ
 */
class KubernetesClientServiceImpl(override val kubernetesClient: KubernetesClient) : KubernetesClientService {
    private val logger = KotlinLogging.logger {}

    override val namespace: String
        get() = kubernetesClient.namespace

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

    override fun readStringSecret(namespace: String, name: String): Map<String, String>? {
        return kubernetesClient.readStringSecret(namespace, name)
    }

    override fun applyStringSecret(
        namespace: String,
        name: String,
        data: Map<String, String>,
        labels: Map<String, String>
    ) {
        kubernetesClient.applyStringSecret(namespace, name, data, labels)
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

    override fun applyIngress(namespace: String, ingress: Ingress) {
        kubernetesClient.applyIngress(namespace, ingress)
    }

    override fun removeIngressWithHost(namespace: String, hostname: String): Boolean {
        return kubernetesClient.removeIngressWithHost(namespace, hostname)
    }
}
