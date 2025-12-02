package io.santorini.model

import kotlin.js.JsExport

/**
 * santorini 管理的资源
 * @author CJ
 */
@JsExport
enum class ResourceType(val fields: List<ResourceFieldDefinition>) {
    Mysql(listOf(ResourceFieldDefinition(""))),
}


data class ResourceFieldDefinition(
    val name: String,
)
