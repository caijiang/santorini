package io.santorini.console

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.console.schema.HostService
import io.santorini.service.KubernetesClientService
import org.koin.ktor.ext.get as koinGet

private val logger = KotlinLogging.logger {}

/**
 * @author CJ
 */
internal fun Application.configureConsoleHostV2() {
    val kubernetesClientService = koinGet<KubernetesClientService>()
    val hostService = koinGet<HostService>()
    // 一般人员可以读取 env
    routing {
        get("/hostsV2/{envId}") {
            val envId = call.pathParameters["envId"]!!
            val list = try {
                kubernetesClientService.readIngressHostFromNamespace(envId)
            } catch (e: Exception) {
                logger.error(e) { "readIngressHostFromNamespace时,响应缺省" }
                hostService.read()
            }
            call.respond(list)
        }
    }
}