package io.santorini.console

import io.fabric8.kubernetes.client.KubernetesClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.well.StatusException
import io.santorini.withAuthorization

/**
 * 有一些不方便直接通过 kubernetes api 获取时
 */
internal fun Application.configureConsoleCrossKubernetes(kubernetesClient: KubernetesClient) {

    routing {
        get("/dockerConfigJsonSecretNames/{namespace}") {
            withAuthorization {
                val namespace = call.pathParameters["namespace"] ?: throw StatusException(HttpStatusCode.BadRequest)
                val names = kubernetesClient.secrets().inNamespace(namespace)
                    .list()
                    .items
                    .filter {
                        "kubernetes.io/dockerconfigjson".equals(it.type, ignoreCase = true)
                    }.map { it.metadata.name }
                call.respond(names)
            }
        }
    }
}