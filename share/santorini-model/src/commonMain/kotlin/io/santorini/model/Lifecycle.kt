package io.santorini.model

import kotlinx.serialization.Serializable

/**
 * @author CJ
 */
@Serializable
data class Lifecycle(
    /**
     * [参考](https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/#lifecycle)
     */
    val terminationGracePeriodSeconds: Int? = null,
    /**
     * 存活探针，不存活就要销毁
     * [参考](https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/#lifecycle-1)
     */
    val livenessProbe: Probe? = null,
    /**
     * 就绪探针,不就绪服务不会来访问
     */
    val readinessProbe: Probe? = null,
    /**
     * 启动探针,成功表示容器已经完成启动，该探针只会成功被执行一次;在成功前其他探针都不会活动
     */
    val startupProbe: Probe? = null,
)
