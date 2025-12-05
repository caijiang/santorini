package io.santorini.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.resources.*
import io.santorini.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.json.jsonb
import java.time.OffsetDateTime

// 这里有部分的数据是我们认为业务上服务端并不关心,只是帮忙存取而已
// 技术细节
// 读取时,我们准备向客户端渲染某段 json 内容时,对 2 个 string 进行合并(json方式)
// 写入时,各管各的

private val logger = KotlinLogging.logger {}

@Suppress("OPT_IN_USAGE")
@Serializable
@JsonIgnoreUnknownKeys
data class ServiceMetaData(
    val id: String,
    val name: String,
    val type: ServiceType,
    val requirements: List<ResourceRequirement>?,
)

inline fun <reified T> mergeJson(jsonData: T, otherJson: String?): String {
    val jsonText = Json.encodeToString(jsonData)
    val other = otherJson?.let { jacksonObjectMapper().readTree(it) } ?: return jsonText
    val root = jacksonObjectMapper().readTree(jsonText) as? ObjectNode
        ?: throw IllegalArgumentException("jsonData:${jsonData} 必须是可以被序列化成 JSON Object的")
    if (!other.isObject)
        throw IllegalArgumentException("otherJson:$otherJson 必须是一个 JSON Object")
    other.fieldNames().forEach { fieldName ->
        if (root[fieldName] != null)
            throw IllegalStateException("存在重复的健名:$fieldName")

        root.putIfAbsent(fieldName, other[fieldName])
    }
    return root.toPrettyString()
}

inline fun <reified T> receiveFromJson(jsonText: String): Pair<T, JsonNode> {
    val data = Json.decodeFromString<T>(jsonText)
    val root = jacksonObjectMapper().readTree(jsonText) as? ObjectNode
        ?: throw IllegalArgumentException("jsonText:${jsonText} 必须是可以被序列化成 JSON Object的")
    val dataJson = jacksonObjectMapper().readTree(Json.encodeToString(data)) as? ObjectNode
        ?: throw IllegalArgumentException("核心对象必须是一个对象:$data")
    dataJson.fieldNames().forEach { fieldName ->
        root.remove(fieldName)
    }
    return data to root
}

@Resource("/services")
@Serializable
data class ServiceMetaResource(
    override val limit: Int? = null,
    override val offset: Int? = null,
    val keyword: String? = null,
) : Pageable {

    @Resource("{id}")
    @Serializable
    data class Id(val parent: ServiceMetaResource = ServiceMetaResource(), val id: String)

    @Resource("{id}/lastRelease/{env}")
    @Serializable
    data class LastRelease(val parent: ServiceMetaResource = ServiceMetaResource(), val id: String, val env: String)
}

class ServiceMetaService(database: Database) {
    object ServiceMetas : IdTable<String>() {
        override val id = varchar("id", 63).entityId()
        val name = varchar("name", length = 50)
        val type = enumerationByName("type", 10, ServiceType::class)
        val requirements = jsonb<List<ResourceRequirement>>("requirements", Json).nullable()
        val createTime = timestampWithTimeZone("createTime")
        override val primaryKey = PrimaryKey(id)
    }

    //        override val id = reference("id", ServiceMetas)

    object ServiceMetaOthers : Table() {
        val id = reference("id", ServiceMetas).uniqueIndex()
        val data = text("data")
        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(ServiceMetas)
            SchemaUtils.create(ServiceMetaOthers)
            val sqls = SchemaUtils.addMissingColumnsStatements(ServiceMetas)
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

    @Suppress("unused")
    suspend fun createOrUpdate(context: Pair<ServiceMetaData, JsonNode>) {
        dbQuery {
            val (serviceMetaData, other) = context
            val now = ServiceMetas.select(ServiceMetas.id)
                .where { ServiceMetas.id eq serviceMetaData.id }
                .map { it[ServiceMetas.id] }
            if (now.isEmpty()) {
                doCreate(serviceMetaData, other)
            } else {
                ServiceMetas.update({ ServiceMetas.id eq serviceMetaData.id }) {
                    it[name] = serviceMetaData.name
                    it[requirements] = serviceMetaData.requirements
                }
                ServiceMetaOthers.update({ ServiceMetas.id eq serviceMetaData.id }) {
                    it[data] = other.toPrettyString()
                }
            }
        }
    }

    suspend fun create(context: Pair<ServiceMetaData, JsonNode>) {
        dbQuery {
            val (serviceMetaData, other) = context
            doCreate(serviceMetaData, other)
        }
    }

    private fun doCreate(
        serviceMetaData: ServiceMetaData,
        other: JsonNode
    ): InsertStatement<Number> {
        ServiceMetas.insert {
            it[id] = serviceMetaData.id
            it[createTime] = OffsetDateTime.now()
            it[type] = serviceMetaData.type
            it[name] = serviceMetaData.name
            it[requirements] = serviceMetaData.requirements
        }
        return ServiceMetaOthers.insert {
            it[id] = serviceMetaData.id
            it[data] = other.toPrettyString()
        }
    }

    private fun selectAll(resource: ServiceMetaResource): Query {
        return ServiceMetas.selectAll()
            .where {
                resource.keyword?.let { keyword ->
                    if (keyword.isNotBlank()) {
                        ServiceMetas.id like "%$keyword%" or (ServiceMetas.name like "%$keyword%")
                    } else null
                } ?: Op.TRUE
            }
    }

    suspend fun readAsPage(resource: ServiceMetaResource, request: PageRequest): PageResult<ServiceMetaData> {
        return dbQuery {
            selectAll(resource)
                .mapToPage(request) {
                    ServiceMetaData(
                        id = it[ServiceMetas.id].value,
                        name = it[ServiceMetas.name],
                        type = it[ServiceMetas.type],
                        requirements = it[ServiceMetas.requirements],
                    )
                }
        }
    }

    suspend fun read(resource: ServiceMetaResource): List<ServiceMetaData> {
        return dbQuery {
            selectAll(resource)
                .map {
                    ServiceMetaData(
                        id = it[ServiceMetas.id].value,
                        name = it[ServiceMetas.name],
                        type = it[ServiceMetas.type],
                        requirements = it[ServiceMetas.requirements],
                    )
                }
        }
    }

    suspend fun read(id: String): Pair<ServiceMetaData, String>? {
        return dbQuery {
            ServiceMetas.selectAll()
                .where {
                    ServiceMetas.id eq id
                }
                .map {
                    ServiceMetaData(
                        id = it[ServiceMetas.id].value,
                        name = it[ServiceMetas.name],
                        type = it[ServiceMetas.type],
                        requirements = it[ServiceMetas.requirements],
                    )
                }.firstOrNull()?.let { data ->
                    data to ServiceMetaOthers.selectAll()
                        .where {
                            ServiceMetaOthers.id eq data.id
                        }.map { it[ServiceMetaOthers.data] }
                        .first()
                }
        }
    }
}


