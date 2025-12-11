package io.santorini.tools

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json


/**
 * @return 搞一个标准 client
 */
fun ApplicationTestBuilder.createStandardClient() = createClient {
    install(ContentNegotiation) {
        json(Json)
    }
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.HEADERS
    }
    install(HttpCookies) {
        storage = AcceptAllCookiesStorage()
    }
}