package io.santorini.resources

import io.santorini.kubernetes.SantoriniResource
import io.santorini.model.ResourceType
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer

/**
 * JAVA_OPTS
 * @author CJ
 */
class JvmGenerateContext : GenerateContext {
    private val systemProperties = mutableMapOf<String, String>()

    @OptIn(InternalSerializationApi::class)
    override fun addResource(resource: SantoriniResource, slotName: String?) {
        if (slotName != null) {
            TODO("还不知道怎么搞定命名的资源")
        }
        if (resource.type == ResourceType.Mysql) {
            val data = resource.readResource(MysqlResourceData::class.serializer())
            systemProperties["spring.datasource.username"] = data.username
            systemProperties["spring.datasource.password"] = data.password
            systemProperties["spring.datasource.url"] =
                "jdbc:mysql://${data.host}:${data.port}/${data.database}?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true"
        } else if (resource.type == ResourceType.NacosAuth) {
            val data = resource.readResource(NacosAuthResourceData::class.serializer())
            systemProperties["spring.cloud.nacos.config.server-addr"] = data.serverAddr
            systemProperties["spring.cloud.nacos.discovery.server-addr"] = data.serverAddr
            if (data.accessKey?.isNotBlank() == true && data.secretKey?.isNotBlank() == true) {
                systemProperties["spring.cloud.nacos.username"] = ""
                systemProperties["spring.cloud.nacos.password"] = ""
                systemProperties["spring.cloud.nacos.config.access-key"] = data.accessKey
                systemProperties["spring.cloud.nacos.config.secret-key"] = data.secretKey
                systemProperties["spring.cloud.nacos.discovery.access-key"] = data.accessKey
                systemProperties["spring.cloud.nacos.discovery.secret-key"] = data.secretKey
            } else {
                systemProperties["spring.cloud.nacos.username"] = data.username ?: ""
                systemProperties["spring.cloud.nacos.password"] = data.password ?: ""
            }
        } else if (resource.type == ResourceType.NacosNamespace) {
            val data = resource.readResource(NacosNamespaceResourceData::class.serializer())
            systemProperties["spring.cloud.nacos.config.namespace"] = data.namespace
            systemProperties["spring.cloud.nacos.discovery.namespace"] = data.namespace
        }
        // 注意escape  $
    }

    override fun toEnvResult(): Map<String, String> {
        if (systemProperties.isEmpty()) {
            return emptyMap()
        }
        return mapOf("JAVA_OPTS" to systemProperties.map { "-D${it.key}=${it.value}" }
            .joinToString(" "))
    }
}