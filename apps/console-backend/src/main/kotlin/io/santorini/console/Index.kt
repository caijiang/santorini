package io.santorini.console

import io.fabric8.kubernetes.client.KubernetesClient
import io.ktor.server.application.*
import io.ktor.server.resources.*

fun Application.configureConsole(kubernetesClient: KubernetesClient) {
    install(Resources)
    configureConsoleEnv(kubernetesClient)
    configureConsoleService()
    configureConsoleHost()
    configureConsoleDeployment()
    configureConsoleMisc(kubernetesClient)
    configureConsoleCrossKubernetes(kubernetesClient)
    configureConsoleUser()
    configureConsoleEnvWiki()
}