package io.santorini.schema

import io.ktor.resources.*
import io.santorini.model.Pageable
import io.santorini.schema.ServiceMetaService.ServiceMetas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime

@Serializable
data class HostData(
    val hostname: String,
    val issuerName: String,
    val secretName: String,
)

@Resource("/hosts")
@Serializable
data class HostResource(
    override val limit: Int? = null,
    override val offset: Int? = null,
) : Pageable {

    @Resource("sync")
    @Serializable
    data class Sync(val parent: HostResource = HostResource())
}

/**
 *
 */
class HostService(database: Database) {
    // 基本信息是可以从 ingress 读取的,所以必须一一对应
//    有效标签值：
//    必须为 63 个字符或更少（可以为空）
//    除非标签值为空，必须以字母数字字符（[a-z0-9A-Z]）开头和结尾
//    包含破折号（-）、下划线（_）、点（.）和字母或数字
    object Hosts : IdTable<String>() {
        /**
         * 并非域名，而是一个可以
         */
        override val id = varchar("hostname", 63).entityId()
        val issuerName = varchar("issuerName", 63)
        val secretName = varchar("secretName", 63)
        val createTime = timestampWithTimeZone("createTime")
        override val primaryKey = PrimaryKey(ServiceMetas.id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Hosts)
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        suspendTransaction {
            withContext(Dispatchers.IO) {
                block()
            }
        }

    suspend fun sync(data: List<HostData>): Int {
        return dbQuery {
            val current = Hosts.selectAll()
                .where {
                    Hosts.id inList data.map { it.hostname }
                }
                .map {
                    HostData(
                        hostname = it[Hosts.id].value,
                        issuerName = it[Hosts.issuerName],
                        secretName = it[Hosts.secretName]
                    )
                }

            // 已存在的
            data.filter { input -> current.any { it.hostname == input.hostname } }
                .forEach { input ->
                    current.find { it.hostname == input.hostname }?.let {
                        if (it != input) {
                            throw IllegalArgumentException("相同域名:${it.hostname} 与同步数据不匹配")
                        }
                    }
                }

            val toInserts = data.filter { input -> current.none { it.hostname == input.hostname } }

            if (toInserts.isNotEmpty()) {
                Hosts.batchInsert(toInserts) {
                    this[Hosts.id] = it.hostname
                    this[Hosts.issuerName] = it.issuerName
                    this[Hosts.secretName] = it.secretName
                    this[Hosts.createTime] = OffsetDateTime.now()
                }.size
            } else
                0
        }
    }

    suspend fun create(input: HostData) {
        dbQuery {
            val c = Hosts.select(Hosts.id)
                .where { Hosts.id eq input.hostname }
                .count()
            if (c > 0)
                throw IllegalArgumentException("数据已存在")
            Hosts.insert {
                it[id] = input.hostname
                it[issuerName] = input.issuerName
                it[secretName] = input.secretName
                it[createTime] = OffsetDateTime.now()
            }
        }
    }

    suspend fun read(): List<HostData> {
        return dbQuery {
            Hosts.selectAll()
                .map {
                    HostData(
                        hostname = it[Hosts.id].value,
                        issuerName = it[Hosts.issuerName],
                        secretName = it[Hosts.secretName]
                    )
                }
        }
    }


}