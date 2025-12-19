@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package io.santorini.schema

import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.resources.*
import io.santorini.kubernetes.applyStringSecret
import io.santorini.kubernetes.findResourcesInNamespace
import io.santorini.model.Pageable
import io.santorini.model.ResourceRequirementTools
import io.santorini.resources.GenerateContextHome
import io.santorini.schema.ServiceMetaService.ServiceMetas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.json.jsonb
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}

@Serializable
data class DeploymentDeployData(
    val imageRepository: String,
    val imageTag: String? = null,
    val pullSecretName: List<String>? = null,
    // ResourceRequirement-> type-name ; 没有名字那就""
    val resourcesSupply: Map<String, String>? = null,
    /**
     * 只存在输出中
     */
    val targetResourceVersion: String? = null,
    /**
     * 只存在输出中
     */
    val serviceDataSnapshot: String? = null,
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

    @Resource("{id}/{name}")
    @Serializable
    data class IdAndName(val parent: DeploymentResource = DeploymentResource(), val id: String, val name: String)

    @Resource("{id}")
    @Serializable
    data class Id(val parent: DeploymentResource = DeploymentResource(), val id: String)
}

class DeploymentService(
    database: Database,
    private val kubernetesClient: KubernetesClient,
    private val serviceMetaService: ServiceMetaService
) {

    object Deployments : UUIDTable() {
        val service = reference("service", ServiceMetas)
        val env = reference("env", EnvService.Envs)
        val createTime = timestamp("create_time")
        val imageRepository = varchar("image_repository", 120)
        val imageTag = varchar("image_tag", 50).nullable()
        val pullSecretName = jsonb<List<String>>("pull_secret_name", Json).nullable()
        val resourcesSupply = jsonb<Map<String, String>>("resources_supply", Json).nullable()

        /**
         * 因本次部署获得的版本号
         */
        val targetResourceVersion = varchar("target_resource_version", 38).nullable()

        /**
         * 服务镜像
         */
        val serviceDataSnapshot = text("service_data_snapshot")
    }

    init {
        transaction(database) {
            SchemaUtils.create(Deployments)
            val sqls = SchemaUtils.addMissingColumnsStatements(Deployments)
            sqls.forEach {
                logger.info { "Executing for missing columns:$it" }
                exec(it)
            }
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        suspendTransaction {
            withContext(Dispatchers.IO) {
                block()
            }
        }

    suspend fun deploy(deploy: DeploymentResource.Deploy, data: DeploymentDeployData): UUID {
        val snapshot = serviceMetaService.readFull(deploy.serverId)
            ?: throw IllegalArgumentException("No ServiceMetas found for ${deploy.serverId}")
        return dbQuery {
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

            Deployments.insertAndGetId {
                it[service] = deploy.serverId
                it[env] = deploy.envId
                it[createTime] = Clock.System.now()
                it[imageRepository] = data.imageRepository
                it[imageTag] = data.imageTag
                it[pullSecretName] = data.pullSecretName
                it[resourcesSupply] = data.resourcesSupply
                it[serviceDataSnapshot] = snapshot
            }.value
        }
    }

    suspend fun lastRelease(env: String, service: String): DeploymentDeployData? {
        return dbQuery {
            Deployments.selectAll()
                .where {
                    (Deployments.env eq env).and {
                        Deployments.service eq service
                    }
                }.orderBy(Deployments.createTime to SortOrder.DESC)
                .limit(1)
                .map {
                    DeploymentDeployData(
                        it[Deployments.imageRepository],
                        it[Deployments.imageTag],
                        it[Deployments.pullSecretName],
                        it[Deployments.resourcesSupply],
                        it[Deployments.targetResourceVersion],
                        it[Deployments.serviceDataSnapshot],
                    )
                }.firstOrNull()
        }
    }

    suspend fun updateTargetResourceVersion(id: UUID, version: String): Int {
        return dbQuery {
            Deployments.update({ Deployments.id eq id and Deployments.targetResourceVersion.isNull() }) {
                it[targetResourceVersion] = version
            }
        }
    }

    suspend fun deleteFailedDeploy(id: UUID): Int {
        return dbQuery {
            Deployments.deleteWhere {
                Deployments.id eq id and targetResourceVersion.isNull()
            }
        }
    }
}