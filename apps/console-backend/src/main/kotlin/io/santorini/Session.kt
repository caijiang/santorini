package io.santorini

import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.json.Json
import java.util.Base64

private val key = Base64.getDecoder().decode(System.getenv("SESSION_KEY"))

fun RoutingCall.saveUserData(data: InSiteUserData?) {
    if (data == null) sessions.clear<String>()
    else {
        val e1 = AesGcmCrypto.encrypt(Json.encodeToString(data), key)
        sessions.set(e1)
    }
}

fun RoutingCall.queryUserData(): InSiteUserData? {
    val current = sessions.get<String>() ?: return null
    return Json.decodeFromString<InSiteUserData>(AesGcmCrypto.decrypt(current, key))
}