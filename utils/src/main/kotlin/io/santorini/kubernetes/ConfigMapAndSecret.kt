package io.santorini.kubernetes

import io.fabric8.kubernetes.client.KubernetesClient

fun KubernetesClient.updateOne(
    namespace: String,
    configName: String,
    secret: Boolean,
    name: String,
    value: String
) {
    if (secret) {
        updateOneSecret(namespace, configName, name, value)
    } else {
        updateOneConfigMap(namespace, configName, name, value)
    }
}

fun KubernetesClient.removeOne(
    namespace: String,
    configName: String,
    secret: Boolean,
    name: String,
) {
    if (secret) {
        removeOneSecret(namespace, configName, name)
    } else {
        removeOneConfigMape(namespace, configName, name)
    }
}