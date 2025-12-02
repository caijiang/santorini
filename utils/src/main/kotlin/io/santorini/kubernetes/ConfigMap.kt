package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import java.util.*

fun KubernetesClient.applyStringSecret(namespace: String, name: String, data: Map<String, String>) {
    // 更新尝试是失败的，所以我们来干删除再新增
    val item =
        SecretBuilder()
            .withNewMetadata()
            .withNamespace(namespace)
            .withName(name)
            .withLabels<String, String>(mapOf("santorini.io/manageable" to "true"))
            .endMetadata()
            .withType("Opaque")
            .withImmutable(false)
//            .withStringData<String, String>(data)
            .withData<String, String>(data.mapValues { (_, v) -> Base64.getEncoder().encodeToString(v.toByteArray()) })
            .build()

    try {
        resource(item).create()
    } catch (e: Exception) {
//        println("Error while creating service account and assign roles: ${e.message}")

        secrets().inNamespace(namespace)
            .withName(name)
            .delete()

        resource(item).create()
    }
}