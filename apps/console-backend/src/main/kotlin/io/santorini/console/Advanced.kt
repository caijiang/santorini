package io.santorini.console

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.console.schema.SystemStringResource
import io.santorini.console.schema.SystemStringService
import io.santorini.withAuthorization
import org.koin.ktor.ext.inject
import io.ktor.server.resources.post as postResource
import io.ktor.server.resources.put as putResource

private val logger = KotlinLogging.logger {}

internal fun Application.configureConsoleAdvanced() {
    val service = inject<SystemStringService>()
    routing {
        get<SystemStringResource.OneSystemString> {
            logger.info {
                "get one OneSystemString:$it"
            }

            val rs = service.value.readOne(it.name)
            if (rs == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respondNullable(rs.value)
            }
        }

        //<editor-fold desc="管理 SystemString">
        postResource<SystemStringResource> {
            withAuthorization(
                {
                    it.grantAuthorities?.root == true
                }
            ) {
                service.value.create(call.receive())
                call.respond(HttpStatusCode.Created)
            }
        }
        get<SystemStringResource> {
            withAuthorization(
                {
                    it.grantAuthorities?.root == true
                }
            ) {
                call.respond(service.value.read(null))
            }
        }
        delete<SystemStringResource.OneSystemString> { input ->
            withAuthorization(
                {
                    it.grantAuthorities?.root == true
                }
            ) {
                service.value.delete(input.name)
                call.respond(HttpStatusCode.NoContent)
            }
        }
        putResource<SystemStringResource.OneSystemString> { input ->
            withAuthorization(
                {
                    it.grantAuthorities?.root == true
                }
            ) {
                service.value.update(input.name, call.receive())
                call.respond(HttpStatusCode.NoContent)
            }
        }
        //</editor-fold>
    }
}