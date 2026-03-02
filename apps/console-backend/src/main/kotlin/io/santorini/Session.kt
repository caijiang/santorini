package io.santorini

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.json.Json

private val key = System.getenv("SESSION_KEY")?.toByteArray(Charsets.UTF_8) ?: AesGcmCrypto.generateKey()
private val log = KotlinLogging.logger {}

fun RoutingCall.saveUserData(data: InSiteUserData?) {
    if (data == null) sessions.clear<String>()
    else {
        val e1 = AesGcmCrypto.encrypt(Json.encodeToString(data), key)
        sessions.set(e1)
    }
}

fun ApplicationCall.queryUserData(): InSiteUserData? {
    val current = sessions.get<String>() ?: return null
    log.debug { "Getting user data from data: $current" }
    val s1 = AesGcmCrypto.decrypt(current, key)
    return Json.decodeFromString<InSiteUserData>(s1)
}

/**
 * 按授权
 */
suspend fun RoutingContext.withAuthorization(
    audit: suspend (InSiteUserData) -> Boolean = { true },
    block: suspend RoutingContext.(InSiteUserData) -> Unit
) {
    call.withCallAuthorization(audit) {
        block(it)
    }
}

/**
 * 按授权
 */
suspend fun ApplicationCall.withCallAuthorization(
    audit: suspend (InSiteUserData) -> Boolean = { true },
    block: suspend ApplicationCall.(InSiteUserData) -> Unit
) {
    val user = queryUserData()
    if (user == null) {
        respond(HttpStatusCode.Unauthorized)
    } else {
        if (audit(user)) {
            block(user)
        } else {
            respond(HttpStatusCode.Forbidden)
        }
    }
}