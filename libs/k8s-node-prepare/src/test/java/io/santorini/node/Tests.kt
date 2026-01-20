package io.santorini.node

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream


private val json = Json {
    ignoreUnknownKeys = true
}

internal fun testCommandGenerator(javaClass: Class<Any>): CommandGenerator {
    val generator = CommandGenerator(
        javaClass.getResourceAsStream("/local-build-join.txt")?.bufferedReader()
            ?.readLine() ?: "echo '没有设置后续指令'",
        javaClass.getResourceAsStream("/local-build-registry.json")?.let {
            json.decodeFromStream<PrivateImageRegistry>(it)
        }
    )
    return generator
}