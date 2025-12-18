package io.santorini.model

import kotlinx.serialization.Serializable

/**
 * @author CJ
 */
@Serializable
data class HTTPGetAction(
    val port: Int,
    val path: String,
    val host: String? = null,
    /**
     * HTTP or HTTPS
     */
    val scheme: String? = null,
)
