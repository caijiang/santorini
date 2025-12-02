package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.ConfigMapBuilder
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import java.util.*

fun KubernetesClient.deleteConfigMapAndSecret(namespace: String, name: String) {
    configMaps().inNamespace(namespace)
        .withName(name)
        .delete()
    secrets().inNamespace(namespace)
        .withName(name)
        .delete()
}

fun KubernetesClient.applyStringConfig(
    namespace: String,
    name: String,
    data: Map<String, String>,
    labels: Map<String, String> = mapOf("santorini.io/manageable" to "true")
) {
    val item =
        ConfigMapBuilder()
            .withNewMetadata()
            .withNamespace(namespace)
            .withName(name)
            .withLabels<String, String>(labels)
            .endMetadata()
            .withImmutable(false)
            .withData<String, String>(data)
            .build()

    try {
        resource(item).create()
    } catch (e: Exception) {
        configMaps().inNamespace(namespace)
            .withName(name)
            .delete()
        resource(item).create()
    }
}

fun KubernetesClient.applyStringSecret(
    namespace: String,
    name: String,
    data: Map<String, String>,
    labels: Map<String, String> = mapOf("santorini.io/manageable" to "true")
) {
    // 更新尝试是失败的，所以我们来干删除再新增
    val item =
        SecretBuilder()
            .withNewMetadata()
            .withNamespace(namespace)
            .withName(name)
            .withLabels<String, String>(labels)
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