package io.santorini.easydomain.test

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * @author CJ
 */
@Serializable
@JsonIgnoreUnknownKeys
data class LocalAliyunConfig(
    /**
     * alb 的拼凑是 alb.${region}.aliyuncs.com, vpc 的拼凑是 alb-vpc.${region}.aliyuncs.com
     */
    val region: String,
    val accessKeyId: String,
    val accessKeySecret: String,
    val listenerId: String,
    val domain: String,
    val pk: String,
) {
}
