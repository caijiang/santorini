package io.santorini.well

import io.ktor.http.*

/**
 * @author CJ
 */
class StatusException(
    val status: HttpStatusCode,
    message: String? = null
) : RuntimeException(message)