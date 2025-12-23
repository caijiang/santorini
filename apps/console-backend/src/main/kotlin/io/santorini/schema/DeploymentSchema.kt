@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package io.santorini.schema

import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.resources.*
import io.santorini.defaultFixedOffsetTimeZone
import io.santorini.kubernetes.DeploymentRolloutState
import io.santorini.kubernetes.applyStringSecret
import io.santorini.kubernetes.evaluateDeploymentStatus
import io.santorini.kubernetes.findResourcesInNamespace
import io.santorini.model.*
import io.santorini.resources.GenerateContextHome
import io.santorini.schema.ServiceMetaService.ServiceMetas
import io.santorini.well.StatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.json.jsonb
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

private val logger = KotlinLogging.logger {}

/**
 * 这是作为可重复部署的数据
 */
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

/**
 * 列表展示数据
 */
@Serializable
data class DeploymentDataInList(
    val service: String,
    val env: String,
    // 这里给更多信息
    val operator: UserDataSimple,
    val createTime: LocalDateTime,
    val imageRepository: String,
    val imageTag: String?,
    val expiredVersion: Boolean,
    /**
     * 是否完成/停止部署
     */
    val completed: Boolean,
    /**
     * 是否成功部署
     */
    val successful: Boolean,
)

@Resource("/deployments")
@Serializable
data class DeploymentResource(
    override val limit: Int? = null,
    override val offset: Int? = null,
    val serviceId: String? = null,
    val envId: String? = null,
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

        /**
         * 谁干的
         */
        val operator = reference("operator", UserRoleService.Users)
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
         * 版本已经过期，别再检查了
         */
        val expiredVersion = bool("expired_version")

        /**
         * 是否完成/停止部署
         */
        val completed = bool("completed")

        /**
         * 是否成功部署
         */
        val successful = bool("successful")

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

    /**
     * 部署
     * @param operatorId 操作者
     * @param deploy 部署路径
     * @param data 部署数据
     */
    suspend fun deploy(operatorId: Uuid, deploy: DeploymentResource.Deploy, data: DeploymentDeployData): UUID {
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
                it[expiredVersion] = false
                it[completed] = false
                it[successful] = false
                it[operator] = operatorId.toJavaUuid()
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

    data class DeploymentCheck(
        val id: UUID,
        val namespace: String,
        val service: String,
        val resourceVersion: String
    )

    suspend fun heart() {
        val targets = dbQuery {
            Deployments.selectAll()
                .where {
                    Deployments.targetResourceVersion.isNotNull()
                }
                .andWhere {
                    // 10秒前部署的
                    Deployments.createTime less Clock.System.now().minus(10.seconds)
                }
                .andWhere {
                    Deployments.expiredVersion eq false
                }
                .andWhere {
                    Deployments.completed eq false
                }
                .map {
                    DeploymentCheck(
                        it[Deployments.id].value,
                        it[Deployments.env].value,
                        it[Deployments.service].value,
                        it[Deployments.targetResourceVersion]!!
                    )
                }
        }
        logger.debug { "需要处理的部署物有:$targets" }

        targets.forEach { dc ->
            val current = kubernetesClient.apps().deployments()
                .inNamespace(dc.namespace)
                .withName(dc.service)
                .get()
            logger.debug { "获取到 Deployment:$current" }
            if (current != null) {
                if (current.metadata.resourceVersion != dc.resourceVersion) {
                    logger.info { "目标部署物:$dc 已经过时;应该的版本号:${dc.resourceVersion} 实际的版本号:${current.metadata.resourceVersion}" }
                    dbQuery {
                        Deployments.update({ Deployments.id eq dc.id }) {
                            it[expiredVersion] = true
                        }
                    }
                } else {
                    val state = current.evaluateDeploymentStatus()
                    if (state.finalState) {
                        dbQuery {
                            Deployments.update({ Deployments.id eq dc.id }) {
                                it[successful] = state == DeploymentRolloutState.SUCCESS
                                it[completed] = true
                            }
                        }
                    } else {
                        logger.debug { "目标部署物:$dc 状态尚未稳定:$state" }
                    }
                }
            } else {
                logger.warn { "已部署版本没有找到 deployments:$dc" }
            }
        }

    }

    private suspend fun selectAll(resource: DeploymentResource, userId: Uuid): Query {
        val root = dbQuery {
            UserRoleService.Users.select(UserRoleService.Users.grantAuthorities)
                .where { UserRoleService.Users.id eq userId.toJavaUuid() }
                .map { it[UserRoleService.Users.grantAuthorities] }
                .firstOrNull()
        } ?: throw StatusException(HttpStatusCode.Forbidden)
        // 关联与否取决于是否 root
        return (Deployments innerJoin UserRoleService.Users)
            .select(
                Deployments.fields + UserRoleService.Users.fields
            ).where {
                resource.serviceId?.let {
                    Deployments.service eq it
                } ?: Op.TRUE
            }
            .andWhere {
                resource.envId?.let {
                    Deployments.env eq it
                } ?: Op.TRUE
            }
            .andWhere {
                if (root.root)
                    Op.TRUE
                else
                    Deployments.env.inSubQuery(
                        UserRoleService.UserEnvs
                            .select(UserRoleService.UserEnvs.env)
                            .where { UserRoleService.UserEnvs.user eq userId.toJavaUuid() }
                    ) and Deployments.service.inSubQuery(
                        UserRoleService.UserServiceRoles.select(UserRoleService.UserServiceRoles.service)
                            .where { UserRoleService.UserServiceRoles.user eq userId.toJavaUuid() }
                    )
            }
            .orderBy(
                Deployments.createTime to SortOrder.DESC
            )
    }

    private fun toDeploymentDataInList(
        it: ResultRow,
        timezone: FixedOffsetTimeZone = defaultFixedOffsetTimeZone()
    ): DeploymentDataInList {
        return DeploymentDataInList(
            it[Deployments.service].value,
            it[Deployments.env].value,
            UserDataSimple(
                it[UserRoleService.Users.id].value.toString(),
                it[UserRoleService.Users.name],
                it[UserRoleService.Users.avatarUrl],
                it[UserRoleService.Users.createTime].toLocalDateTime(timezone)
            ),
            it[Deployments.createTime].toLocalDateTime(timezone),
            it[Deployments.imageRepository],
            it[Deployments.imageTag],
            it[Deployments.expiredVersion],
            it[Deployments.completed],
            it[Deployments.successful]
        )
    }

    suspend fun readAsPage(
        resource: DeploymentResource,
        userId: Uuid,
        pageRequest: PageRequest
    ): PageResult<DeploymentDataInList> {
        return dbQuery {
            selectAll(resource, userId)
                .mapToPage(pageRequest) {
                    toDeploymentDataInList(it)
                }
        }
    }

    suspend fun read(resource: DeploymentResource, userId: Uuid): Any {
        return dbQuery {
            selectAll(resource, userId)
                .map {
                    toDeploymentDataInList(it)
                }
        }
    }
}