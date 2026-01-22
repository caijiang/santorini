package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.santorini.kubernetes.model.ClusterResourceStat
import io.santorini.model.ResourceType
import io.santorini.model.ServiceRole
import io.santorini.service.KubernetesClientService

/**
 * @author CJ
 */
class KubernetesClientServiceImpl(override val kubernetesClient: KubernetesClient) : KubernetesClientService {
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
}