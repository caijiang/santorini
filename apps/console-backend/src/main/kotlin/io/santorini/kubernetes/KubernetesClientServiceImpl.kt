package io.santorini.kubernetes

import io.fabric8.kubernetes.client.KubernetesClient
import io.santorini.model.ResourceType
import io.santorini.service.KubernetesClientService

/**
 * @author CJ
 */
class KubernetesClientServiceImpl(override val kubernetesClient: KubernetesClient) : KubernetesClientService {
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
}