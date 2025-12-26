package io.santorini.kubernetes

import io.fabric8.kubernetes.client.KubernetesClient
import java.util.*

fun KubernetesClient.findDockerConfigJson(namespace: String, name: String): String? {
    val item = secrets().inNamespace(namespace)
        .withName(name)
        .get() ?: return null

    val mw = item.data?.get(".dockerconfigjson") ?: return null
    return Base64.getDecoder().decode(mw).toString(Charsets.UTF_8)
}