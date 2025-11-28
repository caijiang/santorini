package io.santorini.console

import io.ktor.server.application.*
import io.ktor.server.resources.*
import org.jetbrains.exposed.sql.Database

fun Application.configureConsole(database: Database) {
    install(Resources)
    configureConsoleEnv(database)
    configureConsoleService(database)
    configureConsoleHost(database)
}