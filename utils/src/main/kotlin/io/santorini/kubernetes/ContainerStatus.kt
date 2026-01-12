package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.ContainerStatus

fun ContainerStatus.fetchDigest(): String? {
    return imageID?.let {
        if (it.contains("@")) {
            val i = it.lastIndexOf('@')
            it.substring(i + 1)
        } else null
    }
}