package io.santorini.console

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.console.schema.EnvResource
import io.santorini.console.schema.EnvWikiService
import io.santorini.console.schema.UserRoleService
import io.santorini.withAuthorization
import org.koin.ktor.ext.inject
import io.ktor.server.resources.delete as deleteResource
import io.ktor.server.resources.put as putResource


/**
 * @author CJ
 */
internal fun Application.configureConsoleEnvWiki() {
    val service = inject<EnvWikiService>().value
    val userRoleService = inject<UserRoleService>()
    // 一般人员可以读取 env
    routing {
        /**
         * 开放访问的
         */
        get("/publicEnvWikis/{envId}/{title}") {
            val target =
                service.listByEnv(call.pathParameters["envId"]!!, title = call.pathParameters["title"]!!).firstOrNull()
            if (target == null) {
                call.respond(HttpStatusCode.NotFound)
            } else if (!target.global) {
                call.respond(HttpStatusCode.Forbidden)
            } else
                call.respond(target.content)
        }
        get<EnvResource.Wiki> { wiki ->
            withAuthorization(
                {
                    userRoleService.value.toUserEnvs(it.id).contains(wiki.id)
                }
            ) { _ ->
                call.respond(service.listByEnv(wiki.id, call.queryParameters["includingRemoved"]?.toBoolean() ?: false))
            }
        }
        post<EnvResource.Wiki> { wiki ->
            withAuthorization(
                {
                    userRoleService.value.toUserEnvs(it.id).contains(wiki.id)
                }
            ) { user ->
                service.createWiki(user.id, wiki.id, call.receive())
                call.respond(HttpStatusCode.Created)
            }
        }
        putResource<EnvResource.Wiki.One> { wiki ->
            withAuthorization(
                {
                    userRoleService.value.toUserEnvs(it.id).contains(wiki.id)
                }
            ) { user ->
                service.editWiki(user.id, wiki.id, wiki.title, call.receive())
                call.respond(HttpStatusCode.NoContent)
            }
        }
        deleteResource<EnvResource.Wiki.One> { wiki ->
            withAuthorization(
                {
                    userRoleService.value.toUserEnvs(it.id).contains(wiki.id)
                }
            ) { user ->
                service.removeWiki(user.id, wiki.id, wiki.title)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}