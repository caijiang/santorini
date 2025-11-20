package io.santorini.schema

import io.ktor.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction


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
}

class EnvService(database: Database) {
    object Envs : Table() {
        /**
         * 即 namespace [规则](https://kubernetes.io/zh-cn/docs/concepts/overview/working-with-objects/names/#names)
         */
        val id = varchar("id", 63)
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
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

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
                .where { Envs.id eq data.name }
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

    suspend fun read(ids: List<String>): List<EnvData> {
        return dbQuery {
            Envs.selectAll()
                .where { Envs.id inList ids }
                .map { EnvData(id = it[Envs.id], name = it[Envs.name], production = it[Envs.production]) }
        }
    }
}
