package io.santorini.console.schema

import io.fabric8.kubernetes.api.model.Quantity
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * https://kubernetes.io/zh-cn/docs/reference/kubernetes-api/workload-resources/horizontal-pod-autoscaler-v2/
 */
enum class MetricTargetType {
    Utilization, Value, AverageValue
}

data class HpaTarget(
    val name: String,
    val type: MetricTargetType,
    val value: BigDecimal,
) {
    fun toHumanReadString(): String {
        return currentToHumanReadString(value)
    }

    fun currentToHumanReadString(current: BigDecimal): String {
        if ("cpu".equals(name, true)) {
            if (type == MetricTargetType.Utilization) {
                return "平均CPU使用率${current.intValueExact()}%"
            }
            if (type == MetricTargetType.Value) {
                return "总CPU用量${current}核"
            }
            if (type == MetricTargetType.AverageValue) {
                return "平均CPU用量${current}核"
            }
        }
        if ("memory".equals(name, ignoreCase = true)) {
            if (type == MetricTargetType.Utilization) {
                return "平均内存使用率${current.intValueExact()}%"
            }
            if (type == MetricTargetType.Value) {
                return "总内存用量${current / (1024.toBigDecimal() * 1024.toBigDecimal())}M"
            }
            if (type == MetricTargetType.AverageValue) {
                return "平均内存用量${current / (1024.toBigDecimal() * 1024.toBigDecimal())}M"
            }
        }
        throw IllegalArgumentException("并不支持:${name}")
    }
}

@Serializable
data class HpaStatusData(
    /**
     * 秒数
     */
    val instant: String,
    val serviceId: String,
    val envId: String,
    val name: String,
    val type: MetricTargetType,
    val value: String,
    val target: String,
)

private val logger = KotlinLogging.logger {}

class HpaStatusService(
    database: Database,
) {
    // 只有一致的名字
    object HpaStatuses : Table() {
        val env = reference("env_id", EnvService.Envs)
        val service = reference("service_id", ServiceMetaService.ServiceMetas)
        val createTime = timestamp("create_time")
        val resourceMetricSourceName = varchar("resource_metric_source_name", "memory".length)
        val targetType = enumeration("target_type", MetricTargetType::class)

        /**
         * 如果是 [MetricTargetType.Utilization] 就用百分比，反之那保存[Quantity.getAmountInBytes]的结果
         */
        val targetValue = decimal("target_value", 20, 3)
        val currentValue = decimal("current_value", 20, 3)

        override val primaryKey = PrimaryKey(
            env,
            service,
            createTime,
            resourceMetricSourceName,
        )
    }

    init {
        logger.info { "Start checking HpaStatuses" }
        transaction(database) {
            SchemaUtils.create(HpaStatuses)
            val sqls = SchemaUtils.addMissingColumnsStatements(HpaStatuses)
            sqls.forEach {
                logger.info { "Executing for missing columns:$it" }
                exec(it)
            }
        }
        logger.info { "End checking HpaStatuses" }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        suspendTransaction {
            withContext(Dispatchers.IO) {
                block()
            }
        }

    suspend fun deleteBy(envId: String, serviceId: String) {
        dbQuery {
            HpaStatuses.deleteWhere {
                service eq serviceId and
                        (env eq envId)
            }
        }
    }

    suspend fun queryTimelineSince(instant: Instant, services: Set<String>, envs: Set<String>): List<HpaStatusData> {
        return dbQuery {
            // 如果相同 name 的, target 不一样，则只保留最新的 target
            HpaStatuses.select(
                HpaStatuses.env,
                HpaStatuses.service,
                HpaStatuses.createTime,
                HpaStatuses.resourceMetricSourceName,
                HpaStatuses.targetType,
                HpaStatuses.targetValue,
                HpaStatuses.currentValue,
            ).where {
                HpaStatuses.createTime greater instant
            }.andWhere {
                HpaStatuses.service.inList(services)
            }.andWhere {
                HpaStatuses.env inList envs
            }.map {
                toHpaStatusData(it)
            }
        }
    }

    private fun toHpaStatusData(row: ResultRow): HpaStatusData {
        return HpaStatusData(
            row[HpaStatuses.createTime].epochSeconds.toString(),
            row[HpaStatuses.service].value,
            row[HpaStatuses.env].value,
            row[HpaStatuses.resourceMetricSourceName],
            row[HpaStatuses.targetType],
            toStringValue(row[HpaStatuses.currentValue]),
            toStringValue(row[HpaStatuses.targetValue]),
        )
    }

    private fun toStringValue(bigDecimal: BigDecimal): String = bigDecimal.toString()

    suspend fun insertTimeline(
        time: Instant,
        envId: String,
        serviceId: String,
        hpaTarget: HpaTarget,
        current: BigDecimal
    ) {
        dbQuery {
            HpaStatuses.deleteWhere {
                (service eq serviceId).and(
                    env eq envId,
                ).and(
                    (resourceMetricSourceName neq hpaTarget.name)
                        .or(targetType neq hpaTarget.type)
                        .or(targetValue neq hpaTarget.value)
                )
            }
            HpaStatuses.insert {
                it[env] = envId
                it[service] = serviceId
                it[createTime] = time
                it[resourceMetricSourceName] = hpaTarget.name
                it[targetType] = hpaTarget.type
                it[targetValue] = hpaTarget.value
                it[currentValue] = current
            }
        }
    }

    /**
     * 删除没有必要的数据，一个礼拜前
     */
    suspend fun deleteUnnecessary() {
        dbQuery {
            HpaStatuses.deleteWhere {
                createTime less (
                        Clock.System.now().minus(7.days)
                        )
            }
        }
    }


}