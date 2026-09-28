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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.junit.jupiter.api.Assumptions
import kotlin.test.Test

private val json = Json {
    ignoreUnknownKeys = true
}

/**
 * 真实外呼飞书的用例：只有本机放了 `local-build-feishu-config.json`、并且**显式声明** `SANTORINI_E2E=true` 时才跑。
 *
 * 开关放在环境变量上而不是"文件在不在"：这个文件就在开发机上，用文件判断等于默认每次真发消息，
 * 飞书的限流/代理抖动会被记成单元测试失败。
 * 要跑：`SANTORINI_E2E=true ./gradlew :apps:console-backend:test --tests "*FeishuServiceImplTest"`
 *
 * 条件不满足时 assume 成跳过而不是静默 return —— 否则报告里显示 PASSED，看起来像测过了。
 */
suspend fun workWithLocalFeishu(javaClass: Class<Any>, block: suspend LocalFeishuConfig.() -> Unit) {
    Assumptions.assumeTrue(
        System.getenv("SANTORINI_E2E") == "true",
        "未声明 SANTORINI_E2E=true，跳过真实外呼飞书的用例"
    )
    val inputStream = javaClass.getResourceAsStream("/local-build-feishu-config.json")
    Assumptions.assumeTrue(inputStream != null, "缺少 local-build-feishu-config.json，跳过真实外呼飞书的用例")
    val data = inputStream!!.use {
        json.decodeFromStream<LocalFeishuConfig>(it)
    }
    block(data)
}

/**
 * @author CJ
 */
class FeishuServiceImplTest {
    @Test
    fun sendMessage() = runTest {
        workWithLocalFeishu(javaClass) {
            val config = this
            val feishuTokenStore = mockk<FeishuTokenStore>()
            var mockToken: FeishuToken? = null
            every {
                feishuTokenStore.query(eq(config.id))
            } returns mockToken
            every {
                feishuTokenStore.save(eq(config.id), any())
            } answers (object : Answer<Unit> {
                override fun answer(call: Call) {
                    mockToken = call.invocation.args[1] as FeishuToken?
                }
            })
            val service = FeishuServiceImpl(
                feishuTokenStore, HttpClient(Apache) {
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