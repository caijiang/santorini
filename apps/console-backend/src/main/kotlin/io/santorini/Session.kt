package io.santorini

import io.github.oshai.kotlinlogging.KotlinLogging
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

fun RoutingCall.queryUserData(): InSiteUserData? {
    val current = sessions.get<String>() ?: return null
    log.debug { "Getting user data from data: $current" }
    val s1 = AesGcmCrypto.decrypt(current, key)
    return Json.decodeFromString<InSiteUserData>(s1)
}