package io.santorini.kubernetes.model

import kotlinx.serialization.Serializable

/**
 * ingress 里读出来的入口信息
 * @author CJ
 */
@Serializable
data class HostData(
    val hostname: String,
    val issuerName: String? = null,
    val secretName: String? = null,
    /**
     * 该入口所在的 namespace。
     *
     * 只在跨 namespace 读取（[io.santorini.kubernetes.KubernetesClientService.readIngressHostFromAllNamespaces]）
     * 时填充；单 namespace 读取、以及由业务侧构造（入库/接口入参）时保持 null。
     */
    val namespace: String? = null,
) {
    fun cleanShot(): HostData {
        return copy(
            issuerName = if (issuerName?.isBlank() == true) null else issuerName,
            secretName = if (secretName?.isBlank() == true) null else secretName
        )
    }
}
