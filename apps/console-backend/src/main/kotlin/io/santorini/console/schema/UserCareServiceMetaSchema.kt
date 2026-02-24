package io.santorini.console.schema

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.resources.*
import io.santorini.OAuthPlatform
import io.santorini.console.schema.UserCareServiceMetaService.UserCareServiceMetas.env
import io.santorini.console.schema.UserCareServiceMetaService.UserCareServiceMetas.service
import io.santorini.console.schema.UserCareServiceMetaService.UserCareServiceMetas.user
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

private val logger = KotlinLogging.logger {}

data class NoticeTargetUser(
    val userId: Uuid,
    val name: String,
    /**
     * 飞书的 openId
     */
    val feishuOpenId: String?
)

@Resource("/cares/{envId}/{serviceId}")
@Serializable
data class UserCareServiceMetaResource(
    val serviceId: String,
    val envId: String
)

class UserCareServiceMetaService(
    database: Database,
) {
    object UserCareServiceMetas : Table() {
        val user = reference("user_id", UserRoleService.Users)
        val env = reference("env_id", EnvService.Envs)
        val service = reference("project_id", ServiceMetaService.ServiceMetas)
        val createTime = timestamp("create_time")

        override val primaryKey = PrimaryKey(
            user,
            env,
            service,
        )
    }

    init {
        logger.info { "Start checking UserCareServiceMetas" }
        transaction(database) {
            SchemaUtils.create(UserCareServiceMetas)
            val sqls = SchemaUtils.addMissingColumnsStatements(UserCareServiceMetas)
            sqls.forEach {
                logger.info { "Executing for missing columns:$it" }
                exec(it)
            }
        }
        logger.info { "End checking UserCareServiceMetas" }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        suspendTransaction {
            withContext(Dispatchers.IO) {
                block()
            }
        }

    suspend fun queryCare(ps: UserCareServiceMetaResource, userId: Uuid): Boolean {
        return dbQuery {
            UserCareServiceMetas.select(
                user.count()
            ).where {
                user eq userId.toJavaUuid() and (
                        env eq ps.envId
                        ) and (
                        service eq ps.serviceId
                        )
            }.map {
                it[user.count()] > 0
            }.firstOrNull() ?: false
        }
    }

    suspend fun careOn(ps: UserCareServiceMetaResource, userId: Uuid) {
        dbQuery {
            UserCareServiceMetas.insert {
                it[user] = userId.toJavaUuid()
                it[env] = ps.envId
                it[service] = ps.serviceId
                it[createTime] = Clock.System.now()
            }
        }
    }

    suspend fun careOff(ps: UserCareServiceMetaResource, userId: Uuid) {
        dbQuery {
            UserCareServiceMetas.deleteWhere {
                user eq userId.toJavaUuid() and (
                        env eq ps.envId
                        ) and (
                        service eq ps.serviceId
                        )
            }
        }
    }

    suspend fun listNoticeTarget(namespace: String, serviceId: String): List<NoticeTargetUser> {
        return dbQuery {
            // 连表查询
            (UserCareServiceMetas innerJoin UserRoleService.Users)
                .select(
                    UserRoleService.Users.id,
                    UserRoleService.Users.thirdPlatform,
                    UserRoleService.Users.thirdId,
                    UserRoleService.Users.name,
                )
                .where {
                    service eq serviceId
                }.andWhere {
                    env eq namespace
                }.map {
                    val platform = it[UserRoleService.Users.thirdPlatform]
                    if (platform == OAuthPlatform.Feishu) {
                        NoticeTargetUser(
                            it[UserRoleService.Users.id].value.toKotlinUuid(),
                            it[UserRoleService.Users.name],
                            it[UserRoleService.Users.thirdId]
                        )
                    } else
                        NoticeTargetUser(
                            it[UserRoleService.Users.id].value.toKotlinUuid(),
                            it[UserRoleService.Users.name],
                            null
                        )
                }
        }
    }


}