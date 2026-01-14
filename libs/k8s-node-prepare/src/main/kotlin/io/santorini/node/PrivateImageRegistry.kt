package io.santorini.node

import kotlinx.serialization.Serializable

/**
 * image 私仓
 * @author CJ
 */
@Serializable
data class PrivateImageRegistry(
    val host: String,
    val ip: String,
    val port: Int,
    /**
     * ca 证书
     */
    val caCertificate: String,
) {
    fun toSandboxImage(): String {
        return "${host}:${port}/library/pause:3.10"
    }
}
