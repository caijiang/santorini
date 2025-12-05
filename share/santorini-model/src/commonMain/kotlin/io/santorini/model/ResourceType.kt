package io.santorini.model

import kotlin.js.JsExport

/**
 * santorini 管理的资源
 * @author CJ
 */
@JsExport
enum class ResourceType(val fields: List<ResourceFieldDefinition>) {
    Mysql(
        listOf(
            ResourceFieldDefinition("username", "用户名", true, false),
            ResourceFieldDefinition("password", "密码", true, true),
            ResourceFieldDefinition("host", "主机", true, false),
            ResourceFieldDefinition("port", "port", true, false),
            ResourceFieldDefinition("database", "数据库", true, false),
        )
    ),
    NacosAuth(
        listOf(
            ResourceFieldDefinition("server-addr", "服务地址", true, false, "host:ip 的形式"),
            ResourceFieldDefinition("username", "用户名", false, false),
            ResourceFieldDefinition("password", "密码", false, true),
            ResourceFieldDefinition("access-key", "access-key", false, false),
            ResourceFieldDefinition("secret-key", "secret-key", false, true),
        )
    ),
    NacosNamespace(
        listOf(ResourceFieldDefinition("namespace", "命名空间", true, false, "是 命名空间ID")),
    )
}

/**
 * 字段定义
 */
@JsExport
data class ResourceFieldDefinition(
    val name: String,
    val label: String,
    val required: Boolean,
    val secret: Boolean,
    val tooltip: String? = null,
)
