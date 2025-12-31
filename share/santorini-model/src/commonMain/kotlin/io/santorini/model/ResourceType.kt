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
            ResourceFieldDefinition("server-addr", "服务地址", true, false, "host:port 的形式"),
            ResourceFieldDefinition("username", "用户名", false, false),
            ResourceFieldDefinition("password", "密码", false, true),
            ResourceFieldDefinition("access-key", "access-key", false, false),
            ResourceFieldDefinition("secret-key", "secret-key", false, true),
        )
    ),
    NacosNamespace(
        listOf(ResourceFieldDefinition("namespace", "命名空间", true, false, "是 命名空间ID")),
    ),
    RocketMQ(
        listOf(
            ResourceFieldDefinition("name-server", "Name Server", true, false, "host:port 的形式"),
            ResourceFieldDefinition("access-key", "access-key", false, false),
            ResourceFieldDefinition("secret-key", "secret-key", false, true),
        )
    ),
    Redis(
        listOf(
            ResourceFieldDefinition("username", "用户名", true, false),
            ResourceFieldDefinition("password", "密码", true, true),
            ResourceFieldDefinition("host", "主机", true, false),
            ResourceFieldDefinition("port", "port", true, false),
//            ResourceFieldDefinition("database", "数据库", true, false),
        )
    ),
    LcSMS(
        listOf(
            ResourceFieldDefinition("sandbox", "沙盒模式", true, false),
            ResourceFieldDefinition("access-key", "access-key", true, false),
            ResourceFieldDefinition("secret-key", "secret-key", true, true),
            ResourceFieldDefinition("url", "URL", true, false),
            ResourceFieldDefinition("template", "模板 id", false, false),
            ResourceFieldDefinition("sign", "签名", false, false),
        )
    ),
    LcOSS(
        listOf(
            ResourceFieldDefinition(
                "endpoint",
                "endpoint",
                true,
                false,
                "like https://oss-cn-guangzhou-internal.aliyuncs.com"
            ),
            ResourceFieldDefinition("access-key", "access-key", true, false),
            ResourceFieldDefinition("secret-key", "secret-key", true, true),
            ResourceFieldDefinition("region", "区域", true, false, "like cn-hangzhou"),
            ResourceFieldDefinition("private-bucket", "内部 bucket", true, false),
            ResourceFieldDefinition("public-bucket", "公开 bucket", true, false),
            ResourceFieldDefinition("dir", "目录", true, false, "like target/"),
            ResourceFieldDefinition(
                "private-domain",
                "内部 domain",
                true,
                false,
                "like https://....oss-cn-guangzhou.aliyuncs.com/"
            ),
            ResourceFieldDefinition(
                "public-domain",
                "公开 domain",
                true,
                false,
                "like https://....oss-cn-guangzhou.aliyuncs.com/"
            ),
        ),
    ),
    LcSchedule(
        listOf(
            ResourceFieldDefinition("env", "env", true, false),
            ResourceFieldDefinition("enable", "enable", true, false, "true or false"),
            ResourceFieldDefinition("access-token", "access-token", true, true),
            ResourceFieldDefinition("enable-auto-register", "enable-auto-register", true, false, "true or false"),
            ResourceFieldDefinition("alarm-email", "alarm-email", true, false),
            ResourceFieldDefinition(
                "admin-addresses",
                "adminAddresses",
                true,
                false,
                "like http://job:8080/lecai-job-admin"
            ),
        )
    ),
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
