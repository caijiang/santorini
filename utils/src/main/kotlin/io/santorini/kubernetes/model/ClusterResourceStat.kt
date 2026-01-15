package io.santorini.kubernetes.model

import kotlinx.serialization.Serializable

/**
 * @author CJ
 */
@Serializable
data class ClusterResourceStat(
    val cpu: ComputeResourceState,
    val memory: ComputeResourceState,
    /**
     * 临时 api 所以名字这么怪
     */
    val podsT0: Map<String, Int>
)
