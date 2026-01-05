package io.santorini.console.schema

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.resources.*
import io.santorini.model.ResourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val logger = KotlinLogging.logger {}

@Serializable
data class EnvData(val id: String? = null, val name: String, val production: Boolean)

@Resource("/envs")
@Serializable
class EnvResource {
    @Resource("batch/{ids}")
    @Serializable
    data class Batch(val parent: EnvResource = EnvResource(), val ids: String)

    @Resource("{id}")
    @Serializable
    data class Id(val parent: EnvResource = EnvResource(), val id: String)

    /**
     * wiki 操作
     */
    @Resource("{id}/wikis")
    @Serializable
    data class Wiki(val parent: EnvResource = EnvResource(), val id: String) {
        @Resource("{title}")
        @Serializable
        data class One(val id: String, val parent: Wiki = Wiki(id = id), val title: String)
    }

    /**
     * 公共共享环境支持获取
     */
    @Resource("{id}/shareEnvs")
    @Serializable
    data class ShareEnv(val parent: EnvResource = EnvResource(), val id: String) {
        @Resource("{name}")
        @Serializable
        data class One(val id: String, val parent: ShareEnv = ShareEnv(id = id), val name: String)
    }

    @Resource("{id}/resources")
    @Serializable
    data class Resources(
        val parent: EnvResource = EnvResource(),
        val id: String,
        val type: ResourceType? = null,
        val name: String? = null
    ) {
        @Resource("{resourceName}")
        @Serializable
        data class One(val id: String, val parent: Resources = Resources(id = id), val resourceName: String)
    }
}

@Serializable
data class EnvShareEnv(
    val name: String,
    val value: String? = null,
    val secret: Boolean,
)

class EnvService(database: Database) {
    object Envs : IdTable<String>() {
        /**
         * 即 namespace [规则](https://kubernetes.io/zh-cn/docs/concepts/overview/working-with-objects/names/#names)
         */
        override val id = varchar("id", 63).entityId()
        val name = varchar("name", length = 50)
//        val name2 = varchar("name2", length = 50).nullable()

        /**
         * 是否生产环境
         */
        val production = bool("production")

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Envs)
            val sqls = SchemaUtils.addMissingColumnsStatements(Envs)
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

    suspend fun update(id: String, data: EnvData) {
        dbQuery {
            Envs.update({ Envs.id eq id }) {
                it[name] = data.name
                it[production] = data.production
            }
        }
    }

    suspend fun createOrUpdate(data: EnvData) {
        dbQuery {
            val now = Envs.select(Envs.id)
                .where { Envs.id eq data.id!! }
                .map { it[Envs.id] }
            if (now.isEmpty()) {
                create(data)
            } else {
                update(data.id!!, data)
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    suspend fun create(data: EnvData) {
        dbQuery {
            Envs.insert {
                it[id] = data.id!!
                it[name] = data.name
                it[production] = data.production
            }
        }
    }

    suspend fun read(ids: List<String>?): List<EnvData> {
        return dbQuery {
            Envs.selectAll()
                .where { ids?.let { Envs.id inList it } ?: Op.TRUE }
                .map { EnvData(id = it[Envs.id].value, name = it[Envs.name], production = it[Envs.production]) }
        }
    }

    suspend fun readId(): List<String> {
        return dbQuery {
            Envs.select(Envs.id)
                .map { it[Envs.id].value }
        }
    }
}
