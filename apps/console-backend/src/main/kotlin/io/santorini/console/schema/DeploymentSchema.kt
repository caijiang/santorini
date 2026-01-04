package io.santorini.console.schema

import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.resources.*
import io.santorini.console.model.PageRequest
import io.santorini.console.model.PageResult
import io.santorini.console.model.Pageable
import io.santorini.console.model.mapToPage
import io.santorini.console.resources.GenerateContextHome
import io.santorini.console.schema.ServiceMetaService.ServiceMetas
import io.santorini.defaultFixedOffsetTimeZone
import io.santorini.kubernetes.DeploymentRolloutState
import io.santorini.kubernetes.applyStringSecret
import io.santorini.kubernetes.evaluateDeploymentStatus
import io.santorini.kubernetes.findResourcesInNamespace
import io.santorini.model.ResourceRequirementTools
import io.santorini.service.ImageService
import io.santorini.well.StatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
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
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

private val logger = KotlinLogging.logger {}

/**
 * 预部署执行结果，主要是检查发布
 */
@Serializable
data class PreDeployResult(
    /**
     * 不正常情况就给这个
     */
    val warnMessage: String? = null,
    /**
     * 空的话就是不存在
     */
    val imageDigest: String? = null,
    /**
     * 平台是否满足
     */
    val imagePlatformMatch: Boolean = false,
    /**
     * 成功部署的目标环境
     */
    val successfulEnvs: List<String>? = null,
)


@Serializable
data class ComputeResourceCpu(
    val requestMillis: Int,
    val limitMillis: Int,
)

@Serializable
data class ComputeResourceMemory(
    val requestMiB: Int,
    val limitMiB: Int,
)

@Serializable
data class ComputeResources(
    val cpu: ComputeResourceCpu,
    val memory: ComputeResourceMemory,
)

/**
 * 这是作为可重复部署的数据
 */
@Serializable
@JsonIgnoreUnknownKeys
data class DeploymentDeployData(
    val imageRepository: String,
    val imageTag: String? = null,
    val pullSecretName: List<String>? = null,
    // ResourceRequirement-> type-name ; 没有名字那就""
    val resourcesSupply: Map<String, String>? = null,
    /**
     * 资源是必备的
     */
    val resources: ComputeResources,
    /**
     * 自定义环境变量，它的优先级较高，
     */
    val environmentVariables: Map<String, String>? = null,
    /**
     * 只存在输出中
     */
    val serviceDataSnapshot: String? = null,
    val targetGeneration: Long? = null,
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

    @Resource("preDeploy/{envId}/{serverId}")
    @Serializable
    data class PreDeploy(val parent: DeploymentResource = DeploymentResource(), val serverId: String, val envId: String)

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
    private val serviceMetaService: ServiceMetaService,
    private val imageService: ImageService,
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
        val resources = jsonb<ComputeResources>("resources", Json)
        val environmentVariables = jsonb<Map<String, String>>("environment_variables", Json).nullable()
        val targetGeneration = long("target_generation").nullable()
        val digest = varchar("digest", 71).index()

        /**
         * 版本已经过期，别再检查了
         */
        val expiredVersion = bool("expired_version")

        /**
         * 是否完成/停止部署
         */
        val completed = bool("completed").index()

        /**
         * 是否成功部署
         */
        val successful = bool("successful").index()

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

    suspend fun preDeploy(
        deploy: DeploymentResource.PreDeploy,
        data: DeploymentDeployData
    ): PreDeployResult {
        val imageInfo = try {
            imageService.toImageInfo(deploy.envId, data)
                ?: return PreDeployResult(
                    warnMessage = "镜像不存在",
                )
        } catch (e: Exception) {
            logger.info(e) { "预览部署时" }
            return PreDeployResult(
                warnMessage = e.localizedMessage,
            )
        }

        // 我们要确保是可以访问的
        val systemInfoList = kubernetesClient.nodes().list()
            .items
            .mapNotNull {
                it.status?.nodeInfo
            }

        val match = systemInfoList
            .any { node ->
                imageInfo.second.any {
                    it.os.equals(node.operatingSystem, ignoreCase = true)
                            && it.architecture.equals(node.architecture, ignoreCase = true)
                }
            }
        if (!match) {
            return PreDeployResult(
                warnMessage = "请打包适合${
                    systemInfoList.map { "${it.operatingSystem}/${it.architecture}" }
                        .distinct()
                        .joinToString(",")
                }的镜像",
                imageDigest = imageInfo.first,
                imagePlatformMatch = false
            )
        }

        return PreDeployResult(
            imageDigest = imageInfo.first,
            imagePlatformMatch = true,
            successfulEnvs = dbQuery {
                Deployments.select(Deployments.env)
                    .where {
                        Deployments.digest eq imageInfo.first
                    }
                    .andWhere {
                        (Deployments.successful eq true).and(
                            Deployments.completed eq true
                        )
                    }
                    .withDistinct()
                    .map { it[Deployments.env].value }
            }
        )
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
            val imageInfo = imageService.toImageInfo(deploy.envId, data)
                ?: throw IllegalArgumentException("无法获取镜像")

            kubernetesClient.applyStringSecret(deploy.envId, deploy.serverId + "-env", context.toEnvResult())

            Deployments.insertAndGetId {
                it[service] = deploy.serverId
                it[env] = deploy.envId
                it[createTime] = Clock.System.now()
                it[imageRepository] = data.imageRepository
                it[imageTag] = data.imageTag
                it[digest] = imageInfo.first
                it[pullSecretName] = data.pullSecretName
                it[resourcesSupply] = data.resourcesSupply
                it[Deployments.resources] = data.resources
                it[environmentVariables] = data.environmentVariables
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
                        it[Deployments.resources],
                        it[Deployments.environmentVariables],
                        it[Deployments.serviceDataSnapshot],
                        it[Deployments.targetGeneration],
                    )
                }.firstOrNull()
        }
    }

    suspend fun updateTargetGeneration(id: UUID, version: Long): Int {
        return dbQuery {
            Deployments.update({ Deployments.id eq id and Deployments.targetGeneration.isNull() }) {
                it[targetGeneration] = version
            }
        }
    }

    suspend fun deleteFailedDeploy(id: UUID): Int {
        return dbQuery {
            Deployments.deleteWhere {
                Deployments.id eq id and targetGeneration.isNull()
            }
        }
    }

    data class DeploymentCheck(
        val id: UUID,
        val namespace: String,
        val service: String,
        val generation: Long
    )

    suspend fun heart() {
        val targets = dbQuery {
            Deployments.selectAll()
                .where {
                    Deployments.targetGeneration.isNotNull()
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
                        it[Deployments.targetGeneration]!!
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
                if (current.metadata.generation != dc.generation) {
                    logger.info { "目标部署物:$dc 已经过时;应该的版本号:${dc.generation} 实际的版本号:${current.metadata.generation}" }
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