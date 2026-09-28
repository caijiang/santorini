package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.networking.v1.Ingress
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 创建或覆盖一个 Ingress。
 *
 * 与 [applyStringSecret] 同一策略：先尝试创建，失败则删除重建。
 * 调用方无需预先设置 namespace，这里统一补上。
 */
fun KubernetesClient.applyIngress(namespace: String, ingress: Ingress) {
    val item = IngressBuilder(ingress)
        .editMetadata()
        .withNamespace(namespace)
        .endMetadata()
        .build()

    try {
        resource(item).create()
    } catch (e: Exception) {
        logger.warn(e) { "ingress ${item.metadata?.name} 创建失败，尝试删除重建" }
        network().v1().ingresses().inNamespace(namespace)
            .withName(item.metadata.name)
            .delete()
        resource(item).create()
    }
}

/**
 * 删除 rule host 与 [hostname] 匹配的所有 Ingress
 *
 * @return 是否发生了删除
 */
fun KubernetesClient.removeIngressWithHost(namespace: String, hostname: String): Boolean {
    val targets = network().v1().ingresses().inNamespace(namespace)
        .list().items
        .filter { item -> item.spec?.rules?.any { it.host == hostname } == true }

    if (targets.isEmpty()) return false

    targets.forEach { item ->
        network().v1().ingresses().inNamespace(namespace)
            .withName(item.metadata.name)
            .delete()
        logger.info { "已删除 ingress ${item.metadata.namespace}/${item.metadata.name} (host=$hostname)" }
    }
    return true
}
