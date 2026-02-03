package io.santorini.console

import io.fabric8.kubernetes.client.KubernetesClient
import io.github.caijiang.common.job.scheduler.Scheduler
import io.github.caijiang.common.job.scheduler.support.KtorPersistentJob
import io.github.caijiang.common.job.scheduler.support.KtorTemporaryJob
import io.github.caijiang.common.job.scheduler.support.ServerConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import java.util.*

fun Application.configureConsole(kubernetesClient: KubernetesClient) {
    install(Resources)
    configureConsoleEnv(kubernetesClient)
    configureConsoleService()
    configureConsoleHost()
    configureConsoleDeployment()
    configureConsoleMisc(kubernetesClient)
    configureConsoleCrossKubernetes(kubernetesClient)
    configureConsoleUser()
    configureConsoleEnvWiki()
    configureConsoleAdvanced()
    installScheduler(
        ServerConfig(
            prefix = System.getenv("SCHEDULER_REQUEST_PREFIX") ?: "/santorini/no_body_knows",
        ), get()
    )
}

private fun Application.installScheduler(serverConfig: ServerConfig, scheduler: Scheduler) {
    routing {
        post((serverConfig.prefix ?: "") + "/t/{env}/{hostname}/{type}") {
            val job = KtorTemporaryJob(
                call.parameters["type"]!!, call.receive()
            )
            scheduler.submitTemporaryJob(
                call.parameters["env"]!!,
                call.parameters["hostname"]!!,
                job
            )
            call.respond(HttpStatusCode.NoContent)
        }
        put((serverConfig.prefix ?: "") + "/p/{env}/{hostname}/{type}/{name}") {
            val job = KtorPersistentJob(
                call.parameters["type"]!!,
                call.parameters["name"]!!,
                call.receive()
            )
            scheduler.submitPersistentJob(
                call.parameters["env"]!!,
                call.parameters["hostname"]!!,
                call.parameters["cron"]!!,
                job,
                call.parameters["timezone"]?.let {
                    TimeZone.getTimeZone(it)
                } ?: TimeZone.getDefault()
            )
            call.respond(HttpStatusCode.NoContent)
        }
        delete((serverConfig.prefix ?: "") + "/p/{env}/{hostname}/{name}") {
            scheduler.cleanPersistentJob(
                call.parameters["env"]!!,
                call.parameters["hostname"]!!,
                call.parameters["name"]!!,
            )
            call.respond(HttpStatusCode.NoContent)
        }
    }

}
