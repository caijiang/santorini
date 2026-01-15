package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.PodStatus
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.ResourceRequirements
import io.fabric8.kubernetes.client.KubernetesClient
import io.santorini.kubernetes.model.ClusterResourceStat
import io.santorini.kubernetes.model.ComputeResourceState
import java.math.BigDecimal

private fun isPodReady(status: PodStatus?): Boolean {
    return status?.conditions?.any {
        "Ready".equals(it.type, ignoreCase = true) && "True".equals(it.status, ignoreCase = true)
    } == true
}

private fun ResourceRequirements.requestMemory(): Long {
    val x = requests["memory"] ?: limits["memory"]
    if (x == null) return 0
    return Quantity.getAmountInBytes(x).toLong() / (1024 * 1024)
}

private fun ResourceRequirements.limitMemory(): Long {
    val x = limits["memory"] ?: return 0
    return Quantity.getAmountInBytes(x).toLong() / (1024 * 1024)
}

// 如果你为某个资源指定了限制值，但不指定请求值， 并且没有应用某种准入时机制为该资源设置默认请求值， 那么 Kubernetes 会复制你所指定的限制值，将其用作资源的请求值。
private fun ResourceRequirements.requestCpu(): Long {
    val x = requests["cpu"] ?: limits["cpu"]
    if (x == null) return 0
    return (Quantity.getAmountInBytes(x) * BigDecimal.valueOf(1000)).toLong()
}

private fun ResourceRequirements.limitCpu(): Long {
    val x = limits["cpu"] ?: return 0
    return (Quantity.getAmountInBytes(x) * BigDecimal.valueOf(1000)).toLong()
}

fun KubernetesClient.clusterResourceStat(): ClusterResourceStat {
    val ps = pods().inAnyNamespace()
        .list()
        .items

    val result = ps.groupingBy { isPodReady(it.status).toString() }.eachCount()

    val allResources = ps.flatMap {
        it.spec?.containers ?: emptyList()
    }.mapNotNull {
        it.resources
    }

    val nodes = nodes().list().items

    val metricsAvailable =
        apiServices().list().items
            .filter { svc -> "v1beta1.metrics.k8s.io" == svc.metadata.name }
            .any { svc ->
                svc.status != null && svc.status.conditions != null &&
                        svc.status.conditions.stream().anyMatch { c ->
                            "Available" == c.type &&
                                    "True" == c.status
                        }
            }

    val metrics = if (metricsAvailable) top().pods().metrics().items.flatMap {
        it?.containers ?: emptyList()
    }.mapNotNull { it.usage } else null

//    如果你为某个资源指定了限制值，但不指定请求值， 并且没有应用某种准入时机制为该资源设置默认请求值， 那么 Kubernetes 会复制你所指定的限制值，将其用作资源的请求值。
    return ClusterResourceStat(
        cpu = ComputeResourceState(
            allResources.sumOf {
                it.requestCpu()
            },
            allResources.sumOf {
                it.limitCpu()
            },
            metrics?.mapNotNull {
                it["cpu"]
            }?.sumOf {
                (Quantity.getAmountInBytes(it) * BigDecimal.valueOf(1000)).toLong()
            },
            nodes.mapNotNull {
                it?.status?.capacity?.get("cpu")
            }.sumOf {
                (Quantity.getAmountInBytes(it) * BigDecimal.valueOf(1000)).toLong()
            }
        ),
        memory = ComputeResourceState(
            allResources.sumOf {
                it.requestMemory()
            },
            allResources.sumOf {
                it.limitMemory()
            },
            metrics?.mapNotNull {
                it["memory"]
            }?.sumOf {
                Quantity.getAmountInBytes(it).toLong() / (1024 * 1024)
            },
            nodes.mapNotNull {
                it?.status?.capacity?.get("memory")
            }.sumOf {
                Quantity.getAmountInBytes(it).toLong() / (1024 * 1024)
            }
        ),
        podsT0 = result
    )

}