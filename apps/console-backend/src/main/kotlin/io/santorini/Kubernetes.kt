package io.santorini

import io.fabric8.kubernetes.client.KubernetesClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.kubernetes.createTokenForServiceAccount
import kotlinx.coroutines.future.await

/**
 * 配置 kubernetes 相关功能
 * @author CJ
 */
fun Application.configureKubernetes(client: KubernetesClient) {
    // kubernetes-dashboard 是独立的 service-account
    routing {
        /**
         * 请求新的 kubernetes JWT token
         */
        post("token") {
            val user = call.queryUserData()
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val token = client.createTokenForServiceAccount(user.serviceAccountName, client.namespace).await()
                call.respond(token.status.token)
            }
        }
    }
}
