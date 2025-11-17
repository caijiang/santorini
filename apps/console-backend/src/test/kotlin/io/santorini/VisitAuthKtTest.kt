package io.santorini

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

private val log = KotlinLogging.logger {}

/**
 * @author CJ
 */
class VisitAuthKtTest {


    @Test
    @Disabled
    fun fetchData() {
        runTest {
            val target = OAuthAccessTokenResponse.OAuth2(
                "eyJhbGciOiJFUzI1NiIsImZlYXR1cmVfY29kZSI6IkZlYXR1cmVPQXV0aEpXVFNpZ25fQ04iLCJraWQiOiI3NTcxOTkzMTA2NjIzNTAwMDk0IiwidHlwIjoiSldUIn0.eyJqdGkiOiI3NTczMzc3Njg5MDEzMjM5ODI3IiwiaWF0IjoxNzYzMzE0NDAxLCJleHAiOjE3NjMzMjE2MDEsInZlciI6InYxIiwidHlwIjoiYWNjZXNzX3Rva2VuIiwiY2xpZW50X2lkIjoiY2xpX2E2NWNjNjBmMDBlZTkwMGMiLCJzY29wZSI6ImF1dGg6dXNlci5pZDpyZWFkIiwiYXV0aF9pZCI6Ijc1NzMzNzc2NzU4NTE1NjMwMTAiLCJhdXRoX3RpbWUiOjE3NjMzMTQzOTgsImF1dGhfZXhwIjoxNzk0ODUwMzk4LCJ1bml0IjoiZXVfbmMiLCJ0ZW5hbnRfdW5pdCI6ImV1X25jIiwib3BhcXVlIjp0cnVlLCJlbmMiOiJBaVFrQVFFQ0FNSURBQUVCQXdBQ0FRMEFBd3NMQUFBQUF3QUFBQWRHWldGMGRYSmxBQUFBRUc5aGRYUm9YMjl3WVhGMVpWOXFkM1FBQUFBSVZHVnVZVzUwU1dRQUFBQUJNQUFBQUFSVWFXMWxBQUFBQ2pFM05qSTNNekk0TURBUEFBUU1BQUFBQVFvQUFXTEZJbG12Z0FBaUN3QUNBQUFBRFBNVE9XczNwVEVKUVM1dUd3c0FBd0FBQURCTnY5U2M2UkJlaFNKYjFrazFnV3RYck9oVms1eGROWk9BZ2JtZkcyTDQ1dU9jWU5jUFhTWU9FRDBXcy9HU0tCY0FDd0FGQUFBQUJXVjFYMjVqQUxxbUszMEhzV0twZVVQMW5JN2EveTVZNzkwV1RpOFM0d3ZVWXdqUUxzU2g2SWQ0S2ZvZ251UnNPWWt3M2lDbHd1VXFYR1dXWnJ4Q25xUGpCTVk3WFRTNXl2T0pGcWF2anlSaE5qMUg4bjkwR0pKa0FSc1BvSHluUkZKbE9DZ2htSjYwS0hxZk0wSWo5b0lmbmgzTE5FcHQ1K0VMSlkwbmhKbHN6MFNSYytxeEdBaEtzbytRNTFwejRMNjRNMW9WWmNRN3p3PT0iLCJlbmNfdmVyIjoidjEifQ.4g6uHElNzrSa54XOxN5MjcTdKcAm7kC5zlXNM6TwHynouGvy-ncUJ5AQwwUZk-agN2Y5UFmXirFYAMazmR5JJQ",
                "Bearer",
                0,
                null
            )
            val data = target.fetchData(OAuthPlatform.Feishu, HttpClient(Apache) {
                install(ContentNegotiation) {
                    json(Json)
                }
            })

            log.info { "data: $data" }

        }
    }
}