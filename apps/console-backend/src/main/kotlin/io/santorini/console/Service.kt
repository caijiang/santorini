package io.santorini.console

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.OAuthPlatformUserDataAuditResult
import io.santorini.schema.*
import io.santorini.withAuthorization
import org.jetbrains.exposed.v1.jdbc.Database

private val logger = KotlinLogging.logger {}

internal fun Application.configureConsoleService(database: Database) {
    val service = ServiceMetaService(database)
    // 一般人员可以读取 env
    routing {
        post<ServiceMetaResource> {
            withAuthorization(OAuthPlatformUserDataAuditResult.Manager) {
                val text = call.receiveText()
                val context = receiveFromJson<ServiceMetaData>(text)
                logger.info { "准备新增服务:$context" }
                service.create(context)
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
            logger.info {
                "LastRelease for $it"
            }
            call.respond(HttpStatusCode.OK)
        }
    }
}