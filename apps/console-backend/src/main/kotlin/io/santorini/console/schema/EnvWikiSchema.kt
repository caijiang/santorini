package io.santorini.console.schema

import io.santorini.console.schema.UserRoleService.Users
import io.santorini.defaultFixedOffsetTimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid


@Serializable
data class EnvWikiContent(
    val envId: String? = null,
    val title: String,
    val content: String,
    /**
     * 是否全局可以访问
     */
    val global: Boolean = false,
    val removed: Boolean = false,
    /**
     * 创建或者更新时间
     */
    val time: LocalDateTime? = null,
    /**
     * 最近的维护者
     */
    val operator: UserDataSimple? = null,
)

class EnvWikiService(database: Database) {
    /**
     * 保存的每一份 revision
     */
    object EnvWikiRevisions : Table() {
        val env = reference("env", EnvService.Envs)
        val global = bool("global")
        val title = varchar("title", 50)
        val content = mediumText("content")
        val createTime = timestamp("create_time")

        /**
         * 表示是否已经被删除
         */
        val removed = bool("removed")
        val operator = reference("operator", Users)

//        // 定义窗口函数表达式
//        val rowNum = rowNumber()
//            .over()
//            .partitionBy(title)
//            .orderBy(createTime, SortOrder.DESC)
//            .alias("rn")

        init {
            uniqueIndex(env, title, removed, createTime)
        }
    }

    init {
        transaction(database) {
            SchemaUtils.create(EnvWikiRevisions)
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        suspendTransaction {
            withContext(Dispatchers.IO) {
                block()
            }
        }

    suspend fun removeWiki(userId: Uuid, inputEnvId: String, inputTitle: String) {
        dbQuery {
            assertWikiExisting(inputEnvId, inputTitle)
//            EnvWikiRevisions.update(
//                {
//                    (EnvWikiRevisions.env eq inputEnvId).and {
//                        EnvWikiRevisions.title eq inputTitle
//                    }.and {
//                        EnvWikiRevisions.removed eq true
//                    }
//                }
//            ) {
//                it[title] = inputTitle + "_${System.currentTimeMillis()}"
//            }
            val g = listByEnv(inputEnvId, title = inputTitle).firstOrNull()?.global
            EnvWikiRevisions.insert {
                it[env] = inputEnvId
                it[operator] = userId.toJavaUuid()
                it[createTime] = Clock.System.now()
                it[removed] = true
                it[title] = inputTitle
                it[content] = "空"
                it[global] = g ?: false
            }
        }
    }

    suspend fun editWiki(userId: Uuid, inputEnvId: String, inputTitle: String, inputContent: EnvWikiContent) {
        dbQuery {
            assertWikiExisting(inputEnvId, inputTitle)
            EnvWikiRevisions.insert {
                it[env] = inputEnvId
                it[operator] = userId.toJavaUuid()
                it[createTime] = Clock.System.now()
                it[removed] = false
                it[title] = inputTitle
                it[content] = inputContent.content
                it[global] = inputContent.global
            }
        }
    }

    private fun assertWikiExisting(inputEnvId: String, inputTitle: String) {
        // last record
        if (EnvWikiRevisions.select(EnvWikiRevisions.env.count())
                .where {
                    EnvWikiRevisions.env eq inputEnvId
                }
                .andWhere {
                    EnvWikiRevisions.title eq inputTitle
                }
                .andWhere {
                    EnvWikiRevisions.removed eq false
                }
                .map { it[EnvWikiRevisions.env.count()] > 0 }
                .none {
                    it
                }
        ) {
            throw IllegalArgumentException("wiki不存在")
        }
    }

    suspend fun createWiki(userId: Uuid, inputEnvId: String, inputContent: EnvWikiContent) {
        dbQuery {
            EnvWikiRevisions.insert {
                it[env] = inputEnvId
                it[operator] = userId.toJavaUuid()
                it[createTime] = Clock.System.now()
                it[removed] = false
                it[title] = inputContent.title
                it[content] = inputContent.content
                it[global] = inputContent.global
            }
        }
    }

    suspend fun listByEnv(
        envId: String,
        includingRemoved: Boolean = false,
        title: String? = null,
        timezone: FixedOffsetTimeZone = defaultFixedOffsetTimeZone()
    ): List<EnvWikiContent> {
//        if (!userRoleService.value.toUserEnvs(userId).contains(envId)) {
//            return emptyList()
//        }
        // 连表查询
        return dbQuery {
            val rowNum = rowNumber()
                .over()
                .partitionBy(EnvWikiRevisions.title)
                .orderBy(EnvWikiRevisions.createTime, SortOrder.DESC)
                .alias("rn")

            // 第一个结果集
            val t = EnvWikiRevisions.select(
                EnvWikiRevisions.operator,
                EnvWikiRevisions.title,
                EnvWikiRevisions.content,
                EnvWikiRevisions.removed,
                EnvWikiRevisions.createTime,
                EnvWikiRevisions.global,
                rowNum
            )
                .where(EnvWikiRevisions.env.eq(envId))
                .andWhere {
                    title?.let { EnvWikiRevisions.title eq it } ?: Op.TRUE
                }
                .alias("t")

            // 从结果集中再搜索
            (t.innerJoin(Users, { t[EnvWikiRevisions.operator] }, { Users.id })).select(
                t[EnvWikiRevisions.title],
                t[EnvWikiRevisions.content],
                t[EnvWikiRevisions.removed],
                t[EnvWikiRevisions.createTime],
                t[EnvWikiRevisions.global],
                Users.id,
                Users.name,
                Users.avatarUrl,
                Users.createTime
            ).where {
                t[rowNum] eq 1
            }
                .andWhere {
                    if (includingRemoved) Op.TRUE
                    else t[EnvWikiRevisions.removed] eq false
                }
                .map {
                    EnvWikiContent(
                        envId,
                        it[t[EnvWikiRevisions.title]],
                        it[t[EnvWikiRevisions.content]],
                        it[t[EnvWikiRevisions.global]],
                        it[t[EnvWikiRevisions.removed]],
                        it[t[EnvWikiRevisions.createTime]].toLocalDateTime(timezone),
                        UserDataSimple(
                            it[Users.id].value.toString(),
                            it[Users.name],
                            it[Users.avatarUrl],
                            it[Users.createTime].toLocalDateTime(timezone)
                        )
                    )
                }
        }
    }
}