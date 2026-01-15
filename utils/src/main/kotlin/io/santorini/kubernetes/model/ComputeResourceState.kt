package io.santorini.kubernetes.model

import kotlinx.serialization.Serializable

/**
 * 一项计算资源，单位必须统一！！
 * @author CJ
 */
@Serializable
data class ComputeResourceState(
    val request: Long,
    val limit: Long,
    /**
     * 不支持统计的话，null or undefined
     */
    val used: Long?,
    val capacity: Long
)
