package io.santorini.console.resources

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
        when (resource.type) {
            ResourceType.Mysql -> {
                val data = resource.readResource(MysqlResourceData::class.serializer())
                systemProperties["spring.datasource.username"] = data.username
                systemProperties["spring.datasource.password"] = data.password
                systemProperties["spring.datasource.url"] =
                    "jdbc:mysql://${data.host}:${data.port}/${data.database}?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true"
            }

            ResourceType.NacosAuth -> {
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
            }

            ResourceType.NacosNamespace -> {
                val data = resource.readResource(NacosNamespaceResourceData::class.serializer())
                systemProperties["spring.cloud.nacos.config.namespace"] = data.namespace
                systemProperties["spring.cloud.nacos.discovery.namespace"] = data.namespace
            }

            ResourceType.Redis -> {
                val data = resource.readResource(RedisResourceData::class.serializer())
                systemProperties["lettuce.hosts"] = data.host + ":" + data.port
                systemProperties["lettuce.password"] = data.password
                systemProperties["lettuce.username"] = data.username
                systemProperties["spring.data.redis.host"] = data.host
                systemProperties["spring.data.redis.port"] = data.port
                systemProperties["spring.data.redis.username"] = data.username
                systemProperties["spring.data.redis.password"] = data.password
            }

            ResourceType.RocketMQ -> {
                val data = resource.readResource(RocketMQResourceData::class.serializer())
                systemProperties["rocketmq.name-server"] = data.nameServer
                if (data.accessKey?.isNotBlank() == true && data.secretKey?.isNotBlank() == true) {
                    systemProperties["rocketmq.producer.access-key"] = data.accessKey
                    systemProperties["rocketmq.producer.secret-key"] = data.secretKey
                    systemProperties["rocketmq.consumer.access-key"] = data.accessKey
                    systemProperties["rocketmq.consumer.secret-key"] = data.secretKey
                }
            }

            ResourceType.LcSMS -> {
                systemProperties["thirdparty.sms.storage-mode"] = "mysql"
                resource.readAllProperties().mapKeys {
                    when (it.key) {
                        "template" -> "verifyCodeTemplateId"
                        "sign" -> "sign-name"
                        else -> it.key
                    }
                }.forEach { (t, u) ->
                    systemProperties["thirdparty.sms.${t}"] = u
                }
            }

            ResourceType.LcOSS -> {
                val data = resource.readResource(LcOSSResourceData::class.serializer())
                systemProperties["oss.end-point"] = data.endpoint
                systemProperties["oss.access-key"] = data.accessKey
                systemProperties["oss.access-secret"] = data.secretKey
                systemProperties["oss.pub-bucket"] = data.publicBucket
                systemProperties["oss.prv-bucket"] = data.privateBucket
                systemProperties["oss.region"] = data.region
                systemProperties["oss.domain"] = data.publicDomain
                systemProperties["oss.prv-domain"] = data.privateDomain
                systemProperties["oss.dir"] = data.dir
                systemProperties["oss.expire"] = "1800"
                systemProperties["oss.max-size"] = "50"
            }

            ResourceType.LcSchedule -> {
                val x = mapOf(
                    "executor-fail-strategy" to "fail_alarm",
                    "logRetainDay" to "14",
                    "executor-route-strategy" to "ROUND"
                )
                val all = x + resource.readAllProperties().mapKeys {
                    if (it.key == "admin-addresses") "adminAddresses"
                    else it.key
                }

                all.forEach { (t, u) ->
                    systemProperties["lcschedule.${t}"] = u
                }
                systemProperties["xxl.job.accessToken"] = all["access-token"]!!
            }
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