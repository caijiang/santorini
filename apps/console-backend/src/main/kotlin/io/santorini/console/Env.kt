package io.santorini.console

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.patch
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.OAuthPlatformUserDataAuditResult
import io.santorini.schema.EnvData
import io.santorini.schema.EnvResource
import io.santorini.schema.EnvService
import io.santorini.withAuthorization
import org.jetbrains.exposed.sql.Database

private val logger = KotlinLogging.logger {}

/**
 * 环境，也就是 kubernetes 的 namespace
 * @author CJ
 */
fun Application.configureConsoleEnv(database: Database) {
    install(Resources)
    val service = EnvService(database)
    // 一般人员可以读取 env
    routing {
        get<EnvResource.Batch> {
            withAuthorization {
                logger.info {
                    "Fetching batches...:$it"
                }
                call.respond(service.read(it.ids.split(",")))
            }
        }
        patch<EnvResource.Id> {
            withAuthorization(OAuthPlatformUserDataAuditResult.Manager) {
                val data = call.receive<EnvData>()
                logger.info { "Patching envs...:$it,$data" }
                service.update(it.id, data)
                call.respond(HttpStatusCode.OK)
            }
        }
        post<EnvResource> {
            withAuthorization(OAuthPlatformUserDataAuditResult.Manager) {
                val data = call.receive<EnvData>()
                logger.info { "Posting envs...:$data" }
                if (data.id.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest)
                } else {
                    service.create(data)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}