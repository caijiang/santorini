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
) {
    fun cleanShot(): HostData {
        return copy(
            issuerName = if (issuerName?.isBlank() == true) null else issuerName,
            secretName = if (secretName?.isBlank() == true) null else secretName
        )
    }
}
