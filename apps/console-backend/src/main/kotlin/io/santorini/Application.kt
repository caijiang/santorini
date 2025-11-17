package io.santorini

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    // 它的存在主要是保护 api server
    io.ktor.server.cio.EngineMain.main(args)
}

/**
 * 模块入口
 */
fun Application.consoleModule() {
    consoleModuleEntry()
}

private fun Application.consoleModuleEntry(
    httpClient: HttpClient = HttpClient(Apache) {
        install(ContentNegotiation) {
            json(Json)
//            jackson()
        }
    },
    kubernetesClient: KubernetesClient = KubernetesClientBuilder().build(),
    audit: OAuthPlatformUserDataAudit = EnvOAuthPlatformUserDataAudit
) {
    //    configureSerialization()
    configureSecurity(httpClient, kubernetesClient, audit)
    configureHTTP()
    configureRouting()
}
