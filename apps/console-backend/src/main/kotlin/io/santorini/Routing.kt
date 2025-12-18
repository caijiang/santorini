package io.santorini

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        // health check 是这个，别删了
        get("/") {
            call.respondText("Hello World!")
        }
    }
}
