package io.santorini.console

import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
import io.santorini.AesGcmCrypto
import io.santorini.kubernetes.currentPod
import io.santorini.kubernetes.rootOwner
import io.santorini.service.KubernetesClientService
import io.santorini.withAuthorization
import org.koin.ktor.ext.inject
import java.util.*
import kotlin.time.Duration.Companion.days

private val logger = KotlinLogging.logger {}

internal fun Application.configureConsoleMisc(kubernetesClient: KubernetesClient) {
    val service = inject<KubernetesClientService>()
    routing {
        get("/clusterResourceStat") {
            withAuthorization {
                call.respond(
                    service.value.clusterResourceStat()
                )
            }
        }
        get("/tokenForKubernetesDashboard") {
            logger.info {
                "visit tokenForKubernetesDashboard"
            }
//            call.queryParameters t=加密后的, path 相对路径
            val t = call.queryParameters["t"]!!
            val token =
                AesGcmCrypto.decrypt(t, Base64.getUrlDecoder().decode("hbKxQGIWbR3ptS64UOSDCQMYRu8-3jJybhyZslBVQs4"))

            call.response.cookies
                .append("token", token, expires = GMTDate().plus(1.days), secure = true)
            call.respondRedirect(call.queryParameters["path"] ?: "/")
        }
        // 获取 dashboard 地址
        get("/dashboardHost") {
            val add = System.getenv("DASHBOARD_HOST")
            if (add != null) {
                call.respond(add)
            } else {
                call.respond(HttpStatusCode.OK)
            }
        }
        get("/appName") {
            call.respond(System.getenv("APPNAME") ?: "Santorini")
        }
        // 获取内置的 nacos 地址
        get("/embedNacosServerAddr") {
            val root = kubernetesClient.currentPod().rootOwner(kubernetesClient)
            val instance =
                root.metadata.labels["app.kubernetes.io/instance"] ?: throw Exception("顶级元素不包含实例信息")
            val add = kubernetesClient.services()
                .inNamespace(kubernetesClient.namespace)
                .withName("$instance-santorini-santorini-nacos")
                .get()
                ?.let {
                    it.spec.ports.firstOrNull { port -> port.name == "server" }
                        ?.let { port ->
                            "${it.metadata.name}.${kubernetesClient.namespace}:${port.port}"
                        }
                }
            if (add != null) {
                call.respond(add)
            } else {
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}