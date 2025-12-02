package io.santorini.schema

import io.fabric8.kubernetes.client.KubernetesClient
import io.ktor.resources.*
import io.santorini.kubernetes.applyStringSecret
import io.santorini.kubernetes.findResourcesInNamespace
import io.santorini.model.Pageable
import io.santorini.model.ResourceRequirementTools
import io.santorini.resources.GenerateContextHome
import io.santorini.schema.ServiceMetaService.ServiceMetas
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime


@Serializable
data class DeploymentDeployData(
    val imageRepository: String,
    val imageTag: String? = null,
    val pullSecretName: List<String>? = null,
    // ResourceRequirement-> type-name ; 没有名字那就""
    val resourcesSupply: Map<String, String>? = null,
)

@Resource("/deployments")
@Serializable
data class DeploymentResource(
    override val limit: Int? = null,
    override val offset: Int? = null,
) : Pageable {

    /**
     * 部署
     */
    @Resource("deploy/{envId}/{serverId}")
    @Serializable
    data class Deploy(val parent: DeploymentResource = DeploymentResource(), val serverId: String, val envId: String)
}

class DeploymentService(database: Database, private val kubernetesClient: KubernetesClient) {

    object Deployments : UUIDTable() {
        val service = reference("service", ServiceMetas)
        val env = reference("env", EnvService.Envs)
        val createTime = timestampWithTimeZone("createTime")
        val imageRepository = varchar("image_repository", 120)
        val imageTag = varchar("image_tag", 50).nullable()
        val pullSecretName = array<String>("pull_secret_name").nullable()
        val resourcesSupply = jsonb<Map<String, String>>("resources_supply", Json).nullable()
    }

    init {
        transaction(database) {
            SchemaUtils.create(Deployments)
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun deploy(deploy: DeploymentResource.Deploy, data: DeploymentDeployData) {
        dbQuery {
            val (type, serviceRequirements) = ServiceMetas.select(ServiceMetas.type, ServiceMetas.requirements)
                .where { ServiceMetas.id eq deploy.serverId }
                .map {
                    it[ServiceMetas.type] to it[ServiceMetas.requirements]
                }
                .firstOrNull() ?: throw IllegalArgumentException("No ServiceMetas found for ${deploy.serverId}")

            if (data.resourcesSupply?.any { serviceRequirements?.contains(ResourceRequirementTools.rrFromString(it.key)) != true } == true) {
                throw IllegalArgumentException("服务并未提出该资源需求")
            }
            if (serviceRequirements?.any { data.resourcesSupply?.containsKey(it.toString()) != true } == true) {
                throw IllegalArgumentException("必要的资源需求没有满足")
            }

            // 寻找资源
            val resources = kubernetesClient.findResourcesInNamespace(deploy.envId)

            // 供给器
            val context = GenerateContextHome.contextFor(type)

            data.resourcesSupply?.forEach { (t, u) ->
                val rr = ResourceRequirementTools.rrFromString(t)
                context.addResource(
                    resources.firstOrNull { it.type == rr.type && it.name == u }
                        ?: throw IllegalArgumentException("环境资源:$u 并不存在"),
                    rr.name
                )
            }

            kubernetesClient.applyStringSecret(deploy.envId, deploy.serverId + "-env", context.toEnvResult())

            Deployments.insert {
                it[service] = deploy.serverId
                it[env] = deploy.envId
                it[createTime] = OffsetDateTime.now()
                it[imageRepository] = data.imageRepository
                it[imageTag] = data.imageTag
                it[pullSecretName] = data.pullSecretName
                it[resourcesSupply] = data.resourcesSupply
            }

        }
    }
}