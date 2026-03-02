package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricValueStatus
import io.fabric8.kubernetes.api.model.autoscaling.v2.ResourceMetricSource
import io.santorini.console.schema.HpaTarget
import io.santorini.console.schema.MetricTargetType
import java.math.BigDecimal

fun HorizontalPodAutoscaler.belongs(): Pair<String, String?> {
    val namespace = metadata.namespace
    // scaleTargetRef=CrossVersionObjectReference(apiVersion=apps/v1, kind=Deployment, name=demo-service-137, additionalProperties={}),
    val serviceId = spec?.scaleTargetRef?.name
    return namespace to serviceId
}

fun HorizontalPodAutoscaler.toHpaTarget(): HpaTarget? {
    return spec?.metrics?.firstOrNull {
        it.type == "Resource"
    }?.resource?.toTarget()
}

fun HorizontalPodAutoscaler.toCurrentValue(target: HpaTarget): BigDecimal? {
    return status?.currentMetrics?.firstOrNull {
        it.type == "Resource"
    }?.resource?.current?.toValue(target)
}

private fun MetricValueStatus.toValue(target: HpaTarget): BigDecimal {
    if ("cpu".equals(target.name, true) || "memory".equals(target.name, ignoreCase = true)) {
        if (target.type == MetricTargetType.Utilization) {
            return averageUtilization.toBigDecimal()
        }
        if (target.type == MetricTargetType.Value) {
            return Quantity.getAmountInBytes(value)
        }
        if (target.type == MetricTargetType.AverageValue) {
            return Quantity.getAmountInBytes(averageValue)
        }
    }
    throw IllegalArgumentException("并不支持:${target.name}")
}

private fun ResourceMetricSource.toTarget(): HpaTarget {
    if ("cpu".equals(name, ignoreCase = true) || "memory".equals(name, ignoreCase = true)) {
        if ("Utilization".equals(target.type, ignoreCase = true)) {
            return HpaTarget(
                name, MetricTargetType.Utilization, target.averageUtilization.toBigDecimal()
            )
        }
        if ("Value".equals(target.type, ignoreCase = true)) {
            return HpaTarget(
                name, MetricTargetType.Value, Quantity.getAmountInBytes(target.value)
            )
        }
        if ("AverageValue".equals(target.type, ignoreCase = true)) {
            return HpaTarget(
                name, MetricTargetType.AverageValue, Quantity.getAmountInBytes(target.averageValue)
            )
        }
        throw IllegalArgumentException("并不支持:${target.type}")
    }
    throw IllegalArgumentException("并不支持:${name}")
}

