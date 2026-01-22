package io.santorini.service

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.santorini.kubernetes.SantoriniResource
import io.santorini.kubernetes.model.ClusterResourceStat
import io.santorini.model.ResourceType
import io.santorini.model.ServiceRole

/**
 * 与 kubernetesClient 的所有交互
 * @author CJ
 */
interface KubernetesClientService {
    val kubernetesClient: KubernetesClient

    /**
     * 获取当前 pod 的所有者信息
     */
    fun currentPodRootOwner(): HasMetadata

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

    /**
     * 获取当前集群运行状态
     */
    fun clusterResourceStat(): ClusterResourceStat

    //<editor-fold desc="权限控制">
    /**
     * 移除某个 sa 在某环境的所有权限
     */
    fun removeAllServiceRolesFromNamespace(root: HasMetadata, serviceAccountName: String, namespace: String)

    /**
     * 目标 sa 需要该环境权限，没有就添加
     */
    fun makesureRightEnvRoles(
        root: HasMetadata,
        serviceAccountName: String,
        namespace: String,
        withIngress: Boolean = false
    )

    /**
     * 目标 sa 需要这一系列服务权限
     */
    fun makesureRightServiceRoles(
        root: HasMetadata,
        serviceAccountName: String,
        namespace: String,
        serviceRoles: Map<String, List<ServiceRole>>
    )
    //</editor-fold>

}