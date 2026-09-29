package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.networking.v1.Ingress
import io.fabric8.kubernetes.client.KubernetesClient
import io.santorini.kubernetes.model.ClusterResourceStat
import io.santorini.kubernetes.model.HostData
import io.santorini.model.ResourceType
import io.santorini.model.ServiceRole

/**
 * 与 kubernetesClient 的所有交互
 * @author CJ
 */
interface KubernetesClientService {
    val kubernetesClient: KubernetesClient

    /**
     * 当前所在命名空间
     */
    val namespace: String

    /**
     * 获取当前 pod 的所有者信息
     */
    suspend fun currentPodRootOwner(): HasMetadata

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

    //<editor-fold desc="Secret 键值读写">
    /**
     * 读取 Secret 的 data，value 已按 UTF-8 解码
     *
     * @return Secret 不存在时返回 null
     */
    fun readStringSecret(namespace: String, name: String): Map<String, String>?

    /**
     * 创建或覆盖一个 Opaque Secret 的 data
     */
    fun applyStringSecret(
        namespace: String,
        name: String,
        data: Map<String, String>,
        labels: Map<String, String> = mapOf("santorini.io/manageable" to "true")
    )
    //</editor-fold>

    /**
     * 获取当前集群运行状态
     */
    suspend fun clusterResourceStat(): ClusterResourceStat

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

    /**
     * - 注意去重
     * - 注意过滤无效
     * - 如若发现 hostname 一直，但issuerName,secretName 不一致的；则给于警告
     * @return host相关信息，[HostData.namespace] 为 null
     */
    fun readIngressHostFromNamespace(namespace: String): List<HostData>

    /**
     * 跨全部 namespace 读取入口信息，去重与过滤规则同 [readIngressHostFromNamespace]（逐个 namespace 内部去重）。
     *
     * 与单 namespace 版本的区别只有两点：
     * - 每个 [HostData] 的 [HostData.namespace] 会被填上其来源 namespace
     * - 不同 namespace 之间的同名 host 各自保留（是否冲突由调用方判定）
     *
     * ⚠️ 需要集群级 ingress list 权限。
     */
    fun readIngressHostFromAllNamespaces(): List<HostData>

    //<editor-fold desc="Ingress 管理">
    /**
     * 创建或覆盖一个 Ingress
     */
    fun applyIngress(namespace: String, ingress: Ingress)

    /**
     * 删除 rule host 与 hostname 匹配的所有 Ingress
     *
     * @return 是否发生了删除
     */
    fun removeIngressWithHost(namespace: String, hostname: String): Boolean
    //</editor-fold>
}
