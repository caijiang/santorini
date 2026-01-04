package io.santorini.console.resources

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * @author CJ
 */
@Serializable
@JsonIgnoreUnknownKeys
data class MysqlResourceData(
    val username: String,
    val password: String,
    val host: String,
    val port: String,
    val database: String,
)