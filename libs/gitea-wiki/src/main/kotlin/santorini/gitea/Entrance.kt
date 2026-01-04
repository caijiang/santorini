@file:Suppress("UnusedReceiverParameter", "unused", "UNUSED_PARAMETER")

package santorini.gitea

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.routing.*

/**
 * 用于操作的 token
 */
var giteaToken: String = ""

/**
 * 就表示要进请求了，并且直接转发给 gitea
 */
suspend fun RoutingContext.requestGitea(
    operatorMessage: String,
    httpClient: Lazy<HttpClient>,
    giteaUser: String = System.getenv("GITEA_USER") ?: throw IllegalStateException("GITEA_USER missing"),
) {
    // 带个操作者
    if (giteaToken.isBlank()) {
        httpClient.value.get("https://gitea.com/api/v1/users/${giteaUser}/tokens") {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf("name" to "santorini${System.currentTimeMillis()}", "scopes" to listOf("all"))
            )
        }.apply {

        }
    }

}