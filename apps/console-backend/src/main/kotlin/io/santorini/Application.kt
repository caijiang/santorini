package io.santorini

import io.ktor.server.application.*

fun main(args: Array<String>) {
    // 它的存在主要是保护 api server
    io.ktor.server.cio.EngineMain.main(args)
}

fun Application.module() {
    configureSecurity()
    configureHTTP()
    configureRouting()
}
