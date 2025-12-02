package io.santorini.console

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.schema.HostData
import io.santorini.schema.HostResource
import io.santorini.schema.HostService
import io.santorini.withAuthorization
import org.jetbrains.exposed.sql.Database

private val logger = KotlinLogging.logger {}

internal fun Application.configureConsoleHost(database: Database) {
    val service = HostService(database)
    // 一般人员可以读取 env
    routing {
        post<HostResource.Sync> {
            withAuthorization {
                logger.info {
                    "同步 hosts:$it"
                }
                // 因为这玩意儿是一成不变的，所以只能新增，无法修改
                try {
                    val data = call.receiveNullable<List<HostData>>()
                    if (data.isNullOrEmpty()) {
                        call.respond(0)
                    } else {
                        call.respond(service.sync(data))
                    }

                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
        }
        post<HostResource> {
            withAuthorization {
                try {
                    service.create(call.receive<HostData>())
                    call.respond(HttpStatusCode.OK)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
        }
        get<HostResource> {
            withAuthorization {
                val list = service.read()
                call.respond(list)
            }
        }
    }
}