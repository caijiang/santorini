package io.santorini.console.schema

import io.ktor.resources.*
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

@Serializable
data class SystemStringData(
    val name: String,
    val value: String? = null,
)

@Resource("/systemStrings")
@Serializable
class SystemStringResource {

    @Resource("{name}")
    @Serializable
    data class OneSystemString(val parent: SystemStringResource = SystemStringResource(), val name: String)
}

class SystemStringService(database: Database) {
    object SystemStrings : IdTable<String>() {
        override val id = varchar("id", 63).entityId()
        val data = mediumText("data").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(SystemStrings)
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        suspendTransaction {
            withContext(Dispatchers.IO) {
                block()
            }
        }


    suspend fun update(id: String, data: String?) {
        dbQuery {
            SystemStrings.update({ SystemStrings.id eq id }) {
                it[SystemStrings.data] = data
            }
        }
    }

    suspend fun delete(id: String) {
        dbQuery {
            SystemStrings.deleteWhere { SystemStrings.id eq id }
        }
    }

    suspend fun create(data: SystemStringData) {
        dbQuery {
            SystemStrings.insert {
                it[id] = data.name
                it[SystemStrings.data] = data.value
            }
        }
    }

    suspend fun read(ids: List<String>?): List<SystemStringData> {
        return dbQuery {
            SystemStrings.selectAll()
                .where { ids?.let { SystemStrings.id inList it } ?: Op.TRUE }
                .map { SystemStringData(name = it[SystemStrings.id].value, value = it[SystemStrings.data]) }
        }
    }

    suspend fun readOne(id: String): SystemStringData? {
        return dbQuery {
            SystemStrings.selectAll()
                .where { SystemStrings.id eq id }
                .map { SystemStringData(name = it[SystemStrings.id].value, value = it[SystemStrings.data]) }
                .firstOrNull()
        }
    }
}