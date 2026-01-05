package io.santorini.console.schema

import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.resources.*
import io.santorini.*
import io.santorini.console.model.*
import io.santorini.console.schema.ServiceMetaService.ServiceMetas
import io.santorini.console.schema.UserRoleService.UserEnvs.env
import io.santorini.kubernetes.*
import io.santorini.model.ServiceRole
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
import org.jetbrains.exposed.v1.json.extract
import org.jetbrains.exposed.v1.json.jsonb
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

private val logger = KotlinLogging.logger {}

@Serializable
data class UserDataSimple(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val createTime: LocalDateTime,
)

@Serializable
data class UserData(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val createTime: LocalDateTime,
    val grantAuthorities: GrantAuthorities,
    val serviceAccountName: String
)

@Resource("/users")
@Serializable
data class UserResource(
    override val limit: Int? = null,
    override val offset: Int? = null,
    val keyword: String? = null,
//    val timezone: FixedOffsetTimeZone = FixedOffsetTimeZone(
//        UtcOffset(ZoneOffset.of("Asia/Shanghai")),
//    ),
) : Pageable {
    @Resource("{id}")
    @Serializable
    data class Id(val parent: UserResource = UserResource(), val id: Uuid? = null) {
        /**
         * 获取可见的环境 id
         */
        @Resource("envs")
        @Serializable
        data class Envs(val id: Id = Id()) {
            @Resource("{env}")
            @Serializable
            data class One(val parent: Envs = Envs(), val env: String)
        }

        /**
         * 获取可见的服务
         */
        @Resource("services")
        @Serializable
        data class Services(val id: Id = Id()) {
            @Resource("{service}/{role}")
            @Serializable
            data class One(val parent: Services = Services(), val service: String, val role: ServiceRole)
        }
    }

//    /**
//     * 获取可见的环境 id
//     */
//    @Resource("{id}/envs")
//    @Serializable
//    data class Envs(val parent: UserResource = UserResource(), val id: String? = null)
}

class UserRoleService(
    database: Database,
    private val kubernetesClient: KubernetesClient,
    private val serviceMetaService: ServiceMetaService,
    private val envService: EnvService,
) {
    object Users : UUIDTable() {
        val thirdPlatform = enumerationByName("third_platform", 10, OAuthPlatform::class)
        val thirdId = varchar("third_id", 100)

        /**
         * 这个属性被设定为只有 root 可以修改
         */
        val grantAuthorities = jsonb<GrantAuthorities>("grant_authorities", Json)
        val name = varchar("name", 50)
        val avatarUrl = varchar("avatar_url", 300)

        /**
         * 给邦定的 sa
         */
        val serviceAccountName = varchar("service_account_name", 100)
        val createTime = timestamp("create_time")

        init {
            uniqueIndex(thirdPlatform, thirdId)
        }
    }

    /**
     * 用户，环境的可见关系
     */
    object UserEnvs : Table() {
        val user = reference("user_id", Users)
        val env = reference("env_id", EnvService.Envs)
        val createBy = reference("create_by", Users)
        val createTime = timestamp("create_time")

        init {
            UserEnvs.uniqueIndex(user, env)
        }
    }

    object UserServiceRoles : Table() {
        val user = reference("user_id", Users)
        val service = reference("service_id", ServiceMetas)
        val role = enumeration<ServiceRole>("role")
        val createBy = reference("create_by", Users)

        // datetime(6)
        val createTime = timestamp("create_time")

        init {
            UserServiceRoles.uniqueIndex(user, service, role)
        }
    }

    init {
        transaction(database) {
            SchemaUtils.create(Users)
            SchemaUtils.create(UserEnvs)
            SchemaUtils.create(UserServiceRoles)
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        suspendTransaction {
            withContext(Dispatchers.IO) {
                block()
            }
        }

    /**
     * @return 不会包含[InSiteUserData.platformAccessToken]
     */
    suspend fun oAuthPlatformUserDataToSiteUserData(
        platformUserData: OAuthPlatformUserData,
        result: OAuthPlatformUserDataAuditResult,
        account: ServiceAccount
    ): InSiteUserData {
        return dbQuery {
            val currentRow = Users.selectAll()
                .where {
                    (Users.thirdPlatform eq platformUserData.platform).and { Users.thirdId eq platformUserData.stablePk }
                }.firstOrNull()
            val data = InSiteUserData(
                Uuid.random(),
                null,
                result,
                platformUserData.platform,
                platformUserData.stablePk,
                platformUserData.name,
                platformUserData.avatarUrl,
                "",
                account.metadata.name,
            )
            if (currentRow == null) {
                val id = Users.insertAndGetId {
                    it[thirdPlatform] = platformUserData.platform
                    it[thirdId] = platformUserData.stablePk
                    it[grantAuthorities] = defaultGrantAuthorities(result)
                    it[name] = platformUserData.name
                    it[avatarUrl] = platformUserData.avatarUrl
                    it[serviceAccountName] = account.metadata.name
                    it[createTime] = Clock.System.now()
                }
                data.copy(grantAuthorities = defaultGrantAuthorities(result), id = id.value.toKotlinUuid())
            } else {
                Users.update({ Users.id eq currentRow[Users.id] }) {
                    it[name] = platformUserData.name
                    it[avatarUrl] = platformUserData.avatarUrl
                }
                data.copy(
                    grantAuthorities = currentRow[Users.grantAuthorities],
                    id = currentRow[Users.id].value.toKotlinUuid()
                )
            }
        }
    }

    private fun defaultGrantAuthorities(result: OAuthPlatformUserDataAuditResult): GrantAuthorities {
        if (result == OAuthPlatformUserDataAuditResult.Manager) {
            return GrantAuthorities(root = true, users = true, envs = true, roles = true, assigns = true)
        }
        return GrantAuthorities(root = false, users = false, envs = false, roles = false, assigns = false)
    }

    suspend fun readUserAsPage(
        resource: UserResource,
        request: PageRequest,
        timezone: FixedOffsetTimeZone = defaultFixedOffsetTimeZone()
    ): PageResult<UserData> {
        return dbQuery {
            selectUsers(resource)
                .mapToPage(request, { toUserData(it, timezone) })
        }
    }

    suspend fun readUser(
        resource: UserResource,
        timezone: FixedOffsetTimeZone = defaultFixedOffsetTimeZone()
    ): List<UserData> {
        return dbQuery {
            selectUsers(resource)
                .map { toUserData(it, timezone) }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun toUserData(
        resultRow: ResultRow,
        timezone: FixedOffsetTimeZone = defaultFixedOffsetTimeZone()
    ): UserData {
        val time = resultRow[Users.createTime]
        return UserData(
            resultRow[Users.id].value.toString(),
            resultRow[Users.name],
            resultRow[Users.avatarUrl],
            time.toLocalDateTime(timezone),
            resultRow[Users.grantAuthorities],
            resultRow[Users.serviceAccountName],
        )
    }

    suspend fun userById(fromString: UUID): UserData? {
        return dbQuery {
            Users.selectAll()
                .where { Users.id eq fromString }
                .map { toUserData(it) }
                .firstOrNull()
        }
    }

    private fun selectUsers(resource: UserResource): Query {
        val q = Users.selectAll()
            .where {
                Users.grantAuthorities.extract<String>(".root") neq "true"
            }
        val keyword = resource.keyword
        if (keyword.isNullOrBlank()) {
            return q
        }
        return q
            .andWhere {
                Users.name like "%$keyword%"
            }
    }


    /**
     * @return [EnvService.Envs.id] 获取用户可以访问的环境
     */
    suspend fun toUserEnvs(userId: Uuid): List<String> {
        val user = userById(userId.toJavaUuid())
        return if (user == null) {
            listOf()
        } else if (user.grantAuthorities.root) {
            envService.readId()
        } else {
            envIdsByUserId(userId)
        }
    }

    private suspend fun envIdsByUserId(userId: Uuid): List<String> {
        return dbQuery {
            UserEnvs.select(env)
                .where {
                    UserEnvs.user eq userId.toJavaUuid()
                }
                .map { it[env].value }
        }
    }

    suspend fun addUserEnv(userId: Uuid, envId: String, byUser: Uuid) {
        dbQuery {
            UserEnvs.insert {
                it[user] = userId.toJavaUuid()
                it[env] = envId
                it[createBy] = byUser.toJavaUuid()
                it[createTime] = Clock.System.now()
            }
        }
        kubernetesRoles(userId)
    }

    /**
     * @return 服务和角色
     */
    suspend fun readServiceRoleByUser(userId: Uuid): Map<String, List<ServiceRole>> {
        val user = userById(userId.toJavaUuid()) ?: return emptyMap()
        if (user.grantAuthorities.root) {
            return serviceMetaService.readAllId().associateWith { listOf(ServiceRole.Owner) }
        }
        return dbQuery {
            UserServiceRoles.selectAll()
                .where {
                    UserServiceRoles.user eq userId.toJavaUuid()
                }
                .groupBy {
                    it[UserServiceRoles.service].value
                }.mapValues {
                    it.value.map { resultRow ->
                        resultRow[UserServiceRoles.role]
                    }
                }
        }
    }

    suspend fun assignServiceRole(targetUser: Uuid, serviceId: String, roleId: ServiceRole, byUser: Uuid) {
        dbQuery {
            UserServiceRoles.insert {
                it[user] = targetUser.toJavaUuid()
                it[service] = serviceId
                it[role] = roleId
                it[createBy] = byUser.toJavaUuid()
                it[createTime] = Clock.System.now()
            }
        }
        kubernetesRoles(targetUser)
    }

    suspend fun removeUserEnv(id: Uuid, envId: String) {
        dbQuery {
            UserEnvs.deleteWhere {
                user eq id.toJavaUuid() and (env eq envId)
            }
        }
        kubernetesRoles(id, envId)
    }

    suspend fun removeUserServiceRole(id: Uuid, serviceId: String, roleId: ServiceRole) {
        dbQuery {
            UserServiceRoles.deleteWhere {
                user eq id.toJavaUuid() and (service eq serviceId) and (role eq roleId)
            }
        }
        kubernetesRoles(id)
    }

    private suspend fun kubernetesRoles(userId: Uuid, removeEnv: String? = null) {
        logger.info {
            "重新整理用户:$userId 的权限"
        }
        val userData = userById(userId.toJavaUuid()) ?: return
        val root = kubernetesClient.currentPod().rootOwner()
        removeEnv?.let {
            logger.info {
                "移除其在环境 $removeEnv 的所有权限"
            }
            kubernetesClient.removeAllServiceRolesFromNamespace(root, userData.serviceAccountName, it)
        }
        // 获取每一组权限，检查是否符合要求，应加则加，应减则减
        val envs = toUserEnvs(userId)
        logger.info {
            "其 sa:${userData.serviceAccountName} 其具备以下环境权限:$envs"
        }
        val serviceRoles = dbQuery {
            UserServiceRoles.select(UserServiceRoles.service, UserServiceRoles.role)
                .where {
                    UserServiceRoles.user eq userId.toJavaUuid()
                }.map { it[UserServiceRoles.service].value to it[UserServiceRoles.role] }
                .groupBy({ it.first }, { it.second })
        }

        envs.forEach { envId ->
            logger.info { "确保其具备环境:$envId 权限,以及 各服务身份:$serviceRoles" }
            kubernetesClient.makesureRightEnvRoles(root, userData.serviceAccountName, envId)
            kubernetesClient.makesureRightServiceRoles(root, userData.serviceAccountName, envId, serviceRoles)
        }
    }

}