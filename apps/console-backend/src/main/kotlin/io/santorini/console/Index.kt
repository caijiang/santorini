package io.santorini.console

import io.fabric8.kubernetes.client.KubernetesClient
import io.ktor.server.application.*
import io.ktor.server.resources.*
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.configureConsole(database: Database, kubernetesClient: KubernetesClient) {
    install(Resources)
    configureConsoleEnv(database, kubernetesClient)
    configureConsoleService(database)
    configureConsoleHost(database)
    configureConsoleDeployment(database, kubernetesClient)
}