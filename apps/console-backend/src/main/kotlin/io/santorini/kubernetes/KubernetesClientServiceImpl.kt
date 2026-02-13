package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.kubernetes.model.ClusterResourceStat
import io.santorini.model.ResourceType
import io.santorini.model.ServiceRole
import io.santorini.service.KubernetesClientService
import io.santorini.service.impl.feishu.FeishuToken
import java.util.*

/**
 * @author CJ
 */
class KubernetesClientServiceImpl(override val kubernetesClient: KubernetesClient) : KubernetesClientService {
    private val logger = KotlinLogging.logger {}
    override fun currentPodRootOwner(): HasMetadata = kubernetesClient.currentPod().rootOwner(kubernetesClient)

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

    override fun clusterResourceStat(): ClusterResourceStat = kubernetesClient.clusterResourceStat()
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
}

private fun String.safeInK8s(): String {
    return this.replace("[^a-zA-Z0-9]".toRegex(), "")
}
