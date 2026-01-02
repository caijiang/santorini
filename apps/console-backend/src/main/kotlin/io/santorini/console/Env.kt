package io.santorini.console

import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.patch
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.OAuthPlatformUserDataAuditResult
import io.santorini.kubernetes.*
import io.santorini.model.ResourceType
import io.santorini.schema.*
import io.santorini.withAuthorization
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

private val logger = KotlinLogging.logger {}

@Serializable
data class SantoriniResourceData(
    val type: ResourceType,
    val name: String,
    val description: String?,
    val properties: Map<String, String>,
)

internal const val Share_Env_Config_Name = "santorini-env-share"

/**
 * 环境，也就是 kubernetes 的 namespace
 * @author CJ
 */
internal fun Application.configureConsoleEnv(kubernetesClient: KubernetesClient) {
    val service = inject<EnvService>().value
    val userRoleService = inject<UserRoleService>()
    // 一般人员可以读取 env
    routing {
        get<EnvResource.Batch> {
            withAuthorization { _ ->
                logger.info {
                    "Fetching batches...:$it"
                }
                call.respond(service.read(it.ids.split(",")))
            }
        }
        get<EnvResource> {
            withAuthorization {
                logger.info {
                    "Fetching batches...:$it"
                }
                call.respond(service.read(null))
            }
        }
        patch<EnvResource.Id> {
            withAuthorization({
                it.audit == OAuthPlatformUserDataAuditResult.Manager
            }) { _ ->
                val data = call.receive<EnvData>()
                logger.info { "Patching envs...:$it,$data" }
                service.update(it.id, data)
                call.respond(HttpStatusCode.OK)
            }
        }
        get<EnvResource.Resources> {
            withAuthorization { _ ->
                val list = kubernetesClient.findResourcesInNamespace(it.id, it.type)
                    .filter { r -> it.name.isNullOrBlank() || it.name == r.name }
                    .map { SantoriniResourceData(it.type, it.name, it.description, it.publicProperties) }
                call.respond(list)
            }
        }
        post<EnvResource.Resources> {
            withAuthorization { _ ->
                val data = call.receive<SantoriniResourceData>()
                // 不支持修改
                if (kubernetesClient.findResourcesInNamespace(it.id, data.type).any { it.name == data.name }) {
                    call.respond(HttpStatusCode.BadRequest)
                } else {
                    try {
                        val labels = mapOf(
                            *listOfNotNull(
                                "santorini.io/manageable" to "true",
                                "santorini.io/resource-type" to data.type.name,
                                data.description?.let { "santorini.io/description" to it }).toTypedArray()
                        )
                        // 添加配置或者添加 Secret
                        val defines = data.type.fields
                        defines.filter { !it.secret }.filter {
                            it.required || data.properties.containsKey(it.name)
                        }.associate {
                            val value = data.properties[it.name]
                            if (it.required && value == null) {
                                throw IllegalArgumentException("参数${it.label}是必须的")
                            }
                            it.name to value!!
                        }.let { configData ->
                            if (configData.isNotEmpty()) {
                                kubernetesClient.applyStringConfig(
                                    it.id, data.name, configData,
                                    labels
                                )
                            }
                        }

                        defines.filter { it.secret }.filter {
                            it.required || data.properties.containsKey(it.name)
                        }.associate {
                            val value = data.properties[it.name]
                            if (it.required && value == null) {
                                throw IllegalArgumentException("参数${it.label}是必须的")
                            }
                            it.name to value!!
                        }.let { secretData ->
                            if (secretData.isNotEmpty()) {
                                kubernetesClient.applyStringSecret(
                                    it.id, data.name, secretData,
                                    labels
                                )
                            }
                        }

                        call.respond(HttpStatusCode.OK)
                    } catch (e: Exception) {
                        logger.info(e) { "似乎提交了错误的数据" }
                        call.respond(HttpStatusCode.BadRequest)
                    }
                }
            }
        }
        delete<EnvResource.Resources.One> {
            withAuthorization({
                it.audit == OAuthPlatformUserDataAuditResult.Manager
            }) { _ ->
                kubernetesClient.deleteConfigMapAndSecret(it.id, it.resourceName)
                call.respond(HttpStatusCode.OK)
            }
        }
        post<EnvResource> {
            withAuthorization({
                it.audit == OAuthPlatformUserDataAuditResult.Manager
            }) {
                val data = call.receive<EnvData>()
                logger.info { "Posting envs...:$data" }
                if (data.id.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest)
                } else {
                    service.createOrUpdate(data)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
        //<editor-fold desc="公共环境变量处理">
        get<EnvResource.ShareEnv> {
            withAuthorization({ inSiteUserData ->
                val userId = inSiteUserData.id
                val envs = userRoleService.value.toUserEnvs(userId)
                envs.contains(it.id)
            }) { _ ->
                val x1 = kubernetesClient.configMaps().inNamespace(it.id)
                    .withName(Share_Env_Config_Name)
                    .get()
                    ?.data?.map {
                        EnvShareEnv(it.key, it.value, false)
                    }
                val x2 = kubernetesClient.secrets().inNamespace(it.id)
                    .withName(Share_Env_Config_Name)
                    .get()
                    ?.data?.map {
                        EnvShareEnv(it.key, null, true)
                    }
                val tmp = mutableListOf<EnvShareEnv>()
                x1?.let { tmp.addAll(it) }
                x2?.let { tmp.addAll(it) }

                call.respond(tmp)
            }
        }
        put<EnvResource.ShareEnv.One> {
            withAuthorization({ inSiteUserData ->
                val userId = inSiteUserData.id
                val envs = userRoleService.value.toUserEnvs(userId)
                logger.debug {
                    "当前用户:${userId} 它可以操作的环境:$envs, 命中:${envs.contains(it.id)}"
                }
                envs.contains(it.id)
            }) { _ ->
                val secret = call.queryParameters["secret"]?.toBoolean() ?: false
                val name = it.name
                val value = call.receive<String>()
                // 确保存在
                kubernetesClient.updateOne(it.id, Share_Env_Config_Name, secret, name, value)
                call.respond(HttpStatusCode.NoContent)
            }
        }
        delete<EnvResource.ShareEnv.One> {
            withAuthorization({ inSiteUserData ->
                val userId = inSiteUserData.id
                val envs = userRoleService.value.toUserEnvs(userId)
                envs.contains(it.id)
            }) { _ ->
                val secret = call.queryParameters["secret"]?.toBoolean() ?: false
                val name = it.name
                kubernetesClient.removeOne(it.id, Share_Env_Config_Name, secret, name)
                call.respond(HttpStatusCode.NoContent)
            }
        }
        //</editor-fold>
    }
}