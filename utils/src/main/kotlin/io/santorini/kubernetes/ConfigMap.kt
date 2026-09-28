package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.ConfigMapBuilder
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import java.util.*

internal fun KubernetesClient.updateOneConfigMap(
    namespace: String,
    configName: String,
    name: String,
    value: String
): ConfigMap {
    val wn = configMaps().inNamespace(namespace).withName(configName)
    val current = wn.get()
    if (current != null) {
        current.data[name] = value
        return configMaps().resource(current).update()
    } else {
        return configMaps().resource(
            ConfigMapBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(configName)
                .addToLabels("santorini.io/manageable", "true")
                .endMetadata()
                .addToData(name, value)
                .build()
        ).create()
    }
}

internal fun KubernetesClient.removeOneConfigMape(
    namespace: String,
    configName: String,
    name: String,
) {
    val wn = configMaps().inNamespace(namespace).withName(configName)
    val current = wn.get()
    if (current == null) {
        return
    } else {
        current.data.remove(name)
        configMaps().resource(current).update()
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
            .withData<String, String>(
                data.mapValues { (_, v) ->
                    Base64.getEncoder().encodeToString(v.toByteArray(Charsets.UTF_8))
                }
            )
            .build()

    try {
        resource(item).create()
    } catch (_: Exception) {
//        println("Error while creating service account and assign roles: ${e.message}")

        secrets().inNamespace(namespace)
            .withName(name)
            .delete()

        resource(item).create()
    }
}

/**
 * 读取 Secret 的 data，并把每个 value 按 UTF-8 解码为字符串。
 *
 * @return Secret 不存在时返回 null；存在但没有 data 时返回空 map
 */
fun KubernetesClient.readStringSecret(namespace: String, name: String): Map<String, String>? {
    val item = secrets().inNamespace(namespace).withName(name).get() ?: return null
    return item.data.orEmpty().mapValues { (_, v) ->
        String(Base64.getDecoder().decode(v), Charsets.UTF_8)
    }
}
