package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun KubernetesClient.podsInNewReplicaSet(target: Deployment): List<Pod>? {
    val replicaSets = apps()
        .replicaSets()
        .inNamespace(target.metadata.namespace)
        .withLabel("app.kubernetes.io/name", target.metadata.name)
        .list()
        .items

    logger.debug { "Found ${replicaSets.size} replicases for $target" }

    val latestRs = replicaSets
        .filter { rs ->
            rs.metadata.ownerReferences != null &&
                    rs.metadata.ownerReferences
                        .any { or ->
                            target.kind == or.kind &&
                                    target.metadata.uid.equals(or.uid)
                        }
        } // 按 revision 最大的
        .maxByOrNull {
            it.metadata?.annotations?.getOrDefault("deployment.kubernetes.io/revision", "0")
                ?.toInt() ?: 0
        } ?: return null

    logger.debug { "Found latest replicases for $target" }

    val rsLabels =
        latestRs.spec.selector.matchLabels

    return pods()
        .inNamespace(target.metadata.namespace)
        .withLabels(rsLabels)
        .list()
        .items
}