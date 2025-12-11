@file:OptIn(ExperimentalTime::class)

package io.santorini.schema

import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.resources.*
import io.santorini.kubernetes.currentPod
import io.santorini.kubernetes.findClusterRole
import io.santorini.kubernetes.rootOwner
import io.santorini.model.ResourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

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

class EnvService(database: Database, private val kubernetesClient: KubernetesClient) {
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

        /**
         * 分配环境; ClusterRole -> 生成的名字 -> santorini.io/role: env-id-visitable -> visitableClusterRoleName (每次都会检查,存在就行，没有就再度创建 )
         */
        val visitableRoleName = varchar("visitable-role-name", 63).nullable()
        val lastCheckVisitableRoleTime = timestamp("last-check-visitable-role-time").nullable()

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

    suspend fun read(ids: List<String>): List<EnvData> {
        return dbQuery {
            Envs.selectAll()
                .where { Envs.id inList ids }
                .map { EnvData(id = it[Envs.id].value, name = it[Envs.name], production = it[Envs.production]) }
        }
    }

    /**
     * @return 环境以及可见权限名称
     */
    suspend fun readIdAndVisitableRoleName(): List<Pair<String, String>> {
        val thatTime = Clock.System.now().minus(5.minutes)
        return dbQuery {
            val list1 = Envs.select(Envs.id, Envs.visitableRoleName)
                .where {
                    Envs.visitableRoleName.isNotNull()
                }
                .andWhere {
                    Envs.lastCheckVisitableRoleTime.isNotNull()
                }
                .andWhere {
                    Envs.lastCheckVisitableRoleTime.greater(thatTime)
                }
                .map { it[Envs.id].value to it[Envs.visitableRoleName]!! }
                .toList()
            val list = Envs.select(Envs.id, Envs.visitableRoleName, Envs.lastCheckVisitableRoleTime)
                .where {
                    Envs.visitableRoleName.isNullOrEmpty()
                }
                .orWhere {
                    Envs.lastCheckVisitableRoleTime.isNull()
                }
                .orWhere {
                    Envs.lastCheckVisitableRoleTime.lessEq(thatTime)
                }
                .map { it[Envs.id].value to it[Envs.visitableRoleName] }
                .toList()

            val root = kubernetesClient.currentPod().rootOwner()
            val result = list.map {
                it.first to kubernetesClient.findClusterRole(root, "env-${it.first}-visitable") {
                    addToLabels("santorini.io/version", "1") // 如果版本不兼容 直接做掉
                        .endMetadata()
                        .addToRules(
                            PolicyRuleBuilder()
                                .withApiGroups("")
                                .withResources("namespaces")
                                .withResourceNames(it.first)
                                .withVerbs("list")
                                .build()
                        )
                        .build()
                }.metadata.name
            }
            result.forEach { (id, name) ->
                Envs.update({ Envs.id eq id }) {
                    it[visitableRoleName] = name
                    it[lastCheckVisitableRoleTime] = Clock.System.now()
                }
            }
            result + list1
        }
    }
}
