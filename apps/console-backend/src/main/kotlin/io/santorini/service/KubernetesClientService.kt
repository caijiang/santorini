package io.santorini.service

import io.fabric8.kubernetes.client.KubernetesClient
import io.santorini.kubernetes.SantoriniResource
import io.santorini.model.ResourceType

/**
 * 与 kubernetesClient 的所有交互
 * @author CJ
 */
interface KubernetesClientService {
    val kubernetesClient: KubernetesClient

    //<editor-fold desc="环境资源">

    fun findResourcesInNamespace(namespace: String, type: ResourceType? = null): List<SantoriniResource>

    /**
     * 构建明文资源
     */
    fun createEnvResourceInPlain(
        namespace: String,
        data: Map<String, String>,
        labels: Map<String, String> = mapOf("santorini.io/manageable" to "true")
    )

    /**
     * 构建密文资源
     */
    fun createEnvResourceInSecret(
        namespace: String,
        data: Map<String, String>,
        labels: Map<String, String> = mapOf("santorini.io/manageable" to "true")
    )

    /**
     * 删除环境资源
     */
    fun removeResource(namespace: String, name: String)
    //</editor-fold>

}