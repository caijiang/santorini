package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.rbac.Role
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.santorini.model.ResourceType

/**
 * @author CJ
 */
object Utils {

    @JvmStatic
    fun currentPod(client: KubernetesClient = KubernetesClientBuilder().build()): Pod = client.currentPod()

    @JvmStatic
    fun rootOwner(resource: HasMetadata, client: KubernetesClient = KubernetesClientBuilder().build()): HasMetadata =
        resource.rootOwner(client)

    @JvmStatic
    fun findRole(
        root: HasMetadata,
        roleName: String,
        client: KubernetesClient = KubernetesClientBuilder().build()
    ): Role = client.findRole(root, roleName)

    @JvmStatic
    fun findOrCreateServiceAccountAndAssignRoles(
        client: KubernetesClient = KubernetesClientBuilder().build(),
        root: HasMetadata,
        labels: Map<String, String>,
        vararg roles: Role
    ) = client.findOrCreateServiceAccountAndAssignRoles(root, labels, roles.toList(), emptyList())

    @JvmStatic
    fun createTokenForServiceAccount(
        client: KubernetesClient = KubernetesClientBuilder().build(),
        serviceAccountName: String,
        namespace: String
    ) = client.createTokenForServiceAccount(serviceAccountName, namespace)

    @JvmStatic
    fun findResourcesInNamespace(
        client: KubernetesClient = KubernetesClientBuilder().build(),
        namespace: String,
        type: ResourceType? = null
    ): List<SantoriniResource> = client.findResourcesInNamespace(namespace, type)

}