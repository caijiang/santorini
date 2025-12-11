package io.santorini.console

import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.OAuthPlatformUserDataAuditResult
import io.santorini.schema.*
import io.santorini.withAuthorization
import org.jetbrains.exposed.v1.jdbc.Database

private val logger = KotlinLogging.logger {}

internal fun Application.configureConsoleService(database: Database, kubernetesClient: KubernetesClient) {
    val service = ServiceMetaService(database)
    val deploymentService = DeploymentService(database, kubernetesClient)
    // 一般人员可以读取 env
    routing {
        post<ServiceMetaResource> {
            withAuthorization({
                it.audit == OAuthPlatformUserDataAuditResult.Manager
            }) {
                val text = call.receiveText()
                val context = receiveFromJson<ServiceMetaData>(text)
                logger.info { "准备新增服务:$context" }
                service.create(context)
                call.respond(HttpStatusCode.OK)
            }
        }
        put<ServiceMetaResource.Id> {
            withAuthorization({
                it.audit == OAuthPlatformUserDataAuditResult.Manager
            }) {
                val text = call.receiveText()
                val context = receiveFromJson<ServiceMetaData>(text)
                logger.info { "准备更新服务:$context" }
                service.update(it.id, context)
                call.respond(HttpStatusCode.OK)
            }
        }
        get<ServiceMetaResource> {
            withAuthorization {
                val pr = it.toPageRequest()
                if (pr != null) {
                    call.respond(service.readAsPage(it, pr))
                } else {
                    call.respond(service.read(it))
                }
            }
        }
        get<ServiceMetaResource.Id> {
            withAuthorization {
                val detail = service.read(it.id)
                if (detail != null) {
                    val text = mergeJson(detail.first, detail.second)
                    call.respondText(text, ContentType.Application.Json, HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
        get<ServiceMetaResource.LastRelease> {
            // 可以通过传递参数，获取更强的能力，比如当前版本的发布记录
            logger.info {
                "LastRelease for $it"
            }
            val data = deploymentService.lastRelease(it.env, it.id)
            if (data != null) {
                call.respond(data)
            } else {
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}