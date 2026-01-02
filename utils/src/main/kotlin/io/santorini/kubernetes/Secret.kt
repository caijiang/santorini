package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import java.util.*

internal fun KubernetesClient.updateOneSecret(
    namespace: String,
    configName: String,
    name: String,
    value: String
): Secret {
    val wn = secrets().inNamespace(namespace).withName(configName)
    val current = wn.get()
    if (current != null) {
        current.data[name] = Base64.getEncoder().encodeToString(value.toByteArray())
        return secrets().resource(current).update()
    } else {
        return secrets().resource(
            SecretBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(configName)
                .addToLabels("santorini.io/manageable", "true")
                .endMetadata()
                .addToData(name, Base64.getEncoder().encodeToString(value.toByteArray()))
                .build()
        ).create()
    }
}

internal fun KubernetesClient.removeOneSecret(
    namespace: String,
    configName: String,
    name: String,
) {
    val wn = secrets().inNamespace(namespace).withName(configName)
    val current = wn.get()
    if (current == null) {
        return
    } else {
        current.data.remove(name)
        secrets().resource(current).update()
    }
}

fun KubernetesClient.findDockerConfigJson(namespace: String, name: String): String? {
    val item = secrets().inNamespace(namespace)
        .withName(name)
        .get() ?: return null

    val mw = item.data?.get(".dockerconfigjson") ?: return null
    return Base64.getDecoder().decode(mw).toString(Charsets.UTF_8)
}