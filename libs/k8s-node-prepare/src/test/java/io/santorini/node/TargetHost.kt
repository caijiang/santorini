package io.santorini.node

import kotlinx.serialization.Serializable

/**
 * @author CJ
 */
@Serializable
data class TargetHost(
    val ip: String,
    val privateKeyPemPKCS8: String,
    val proxyJump: String? = null,
)
