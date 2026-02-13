package io.santorini.service.impl.feishu

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.mockk.Answer
import io.mockk.Call
import io.mockk.every
import io.mockk.mockk
import io.santorini.io.santorini.test.LocalFeishuConfig
import io.santorini.service.KubernetesClientService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlin.test.Test

private val json = Json {
    ignoreUnknownKeys = true
}

suspend fun workWithLocalFeishu(javaClass: Class<Any>, block: suspend LocalFeishuConfig.() -> Unit) {
    javaClass.getResourceAsStream("/local-build-feishu-config.json")?.let { inputStream ->
        val data = inputStream.use {
            json.decodeFromStream<LocalFeishuConfig>(it)
        }
        block(data)
    }
}

/**
 * @author CJ
 */
class FeishuServiceImplTest {
    @Test
    fun sendMessage() = runTest {
        workWithLocalFeishu(javaClass) {
            val config = this
            val kubernetesClientService = mockk<KubernetesClientService>()
            var mockToken: FeishuToken? = null
            every {
                kubernetesClientService.queryFeishuToken(eq(config.id))
            } returns mockToken
            every {
                kubernetesClientService.saveFeishuToken(eq(config.id), any())
            } answers (object : Answer<Unit> {
                override fun answer(call: Call) {
                    mockToken = call.invocation.args[1] as FeishuToken?
                }
            })
            val service = FeishuServiceImpl(
                kubernetesClientService, HttpClient(Apache) {
                    install(ContentNegotiation) {
                        json(Json)
//            jackson()
                    }
                }, this.id, this.secret
            )

            service.sendSingleMessage(
                demoUserOpenId, FeishuPost(
                    "测试", listOf(
                        FeishuParagraph(
                            listOf(FeishuTags.text("测试文本啊"))
                        )
                    )
                )
            )
        }
    }
}