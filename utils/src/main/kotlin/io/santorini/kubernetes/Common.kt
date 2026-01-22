package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient

fun HasMetadata.inheritLabels(): Map<String, String> = this.metadata.labels

const val CONFIG_ADDITIONAL_KEY_HOSTNAME = "FAKE_ENV_HOSTNAME"

/**
 * 运行当前 pod的 pod 配置;需要当前 pod 存在一定权限
 */
fun KubernetesClient.currentPod(): Pod {
    val namespace: String = namespace
    val podName =
        configuration.additionalProperties?.get(CONFIG_ADDITIONAL_KEY_HOSTNAME)?.toString() ?: System.getenv("HOSTNAME")

    // 自己的 pod 信息, 然后逐级寻找自己的 owner 信息
    return pods().inNamespace(namespace)
        .withName(podName)
        .get()
}

/**
 * 最顶级的 owner, 我们部署 kubernetes 可能是 deployment 但实际工作的是 pod,这个函数可以获取创造出当前对象的顶级所有者
 */
fun HasMetadata.rootOwner(client: KubernetesClient): HasMetadata {
    // 自己的 pod 信息, 然后逐级寻找自己的 owner 信息
    var i = 0
    var currentResource: HasMetadata = this
    while (i++ < 10) {
        val ownerReferenceList = currentResource
            .metadata
            .ownerReferences
        if (ownerReferenceList.isNullOrEmpty()) {
            return currentResource
        }
        val target = ownerReferenceList.first()
        currentResource = client.genericKubernetesResources(target.apiVersion, target.kind)
            .inNamespace(client.namespace)
            .withName(target.name)
            .get() ?: return currentResource
    }
    return currentResource
}