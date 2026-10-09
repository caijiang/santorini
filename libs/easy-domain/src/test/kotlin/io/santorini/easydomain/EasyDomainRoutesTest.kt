@file:Suppress("NonAsciiCharacters", "RemoveRedundantBackticks")

package io.santorini.easydomain

import io.fabric8.kubernetes.api.model.networking.v1.Ingress
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import io.santorini.kubernetes.KubernetesClientService
import io.santorini.kubernetes.model.HostData
import kotlinx.serialization.json.Json
import kotlin.test.Test

class EasyDomainRoutesTest {

    private val namespace = "easy-domains"

    /** 哨兵值：只要出现在响应体里，就说明凭据被带出去了 */
    private val SECRET_ACCESS_KEY_ID = "test-access-key-id"
    private val SECRET_ACCESS_KEY_SECRET = "test-access-key-secret"

    private fun mockK8s(
        existing: List<HostData> = emptyList(),
        applyResult: () -> Unit = {},
    ): KubernetesClientService {
        val k8s = mockk<KubernetesClientService>()
        every { k8s.readIngressHostFromNamespace(namespace) } returns existing
        every { k8s.applyIngress(namespace, any()) } answers { applyResult() }
        every { k8s.removeIngressWithHost(namespace, any()) } returns false
        return k8s
    }

    private fun config() = EasyDomainConfig.fromEnv { key ->
        when (key) {
            EasyDomainConfig.ENV_NAMESPACE -> namespace
            EasyDomainConfig.ENV_TEMPLATE -> TestFixtures.TEMPLATE_YAML
            else -> null
        }
    }

    /**
     * 阿里云配置齐全的配置：证书同步已启用，但它与路由无关。
     *
     * @param listenerId 传 null 表示只配了账号，没配 ALB 监听 —— 此时证书同步**未**启用
     */
    private fun configWithCertSync(listenerId: String? = null) = EasyDomainConfig.fromEnv { key ->
        when (key) {
            EasyDomainConfig.ENV_NAMESPACE -> namespace
            EasyDomainConfig.ENV_TEMPLATE -> TestFixtures.TEMPLATE_YAML
            EasyDomainConfig.ENV_DNS_CIDRS -> "10.0.0.0/8,100.64.0.0/10"
            EasyDomainConfig.ENV_DNS_CNAME_SUFFIXES -> "example.com"
            EasyDomainConfig.ENV_ALIYUN_ACCESS_KEY_ID -> SECRET_ACCESS_KEY_ID
            EasyDomainConfig.ENV_ALIYUN_ACCESS_KEY_SECRET -> SECRET_ACCESS_KEY_SECRET
            EasyDomainConfig.ENV_ALIYUN_REGION -> "cn-hangzhou"
            EasyDomainConfig.ENV_ALIYUN_ALB_LISTENER_ID -> listenerId
            else -> null
        }
    }

    /**
     * 断言状态码，**失败时把响应体一并带出**。
     *
     * 裸 `assertEquals(OK, status)` 的失败信息只有 expected/actual，而 Ktor 路由出问题时
     * 真正有价值的是 body 里写了什么（错误详情、校验提示）。把它绑进同一条失败信息，
     * 省掉"改断言 -> 重跑 -> 看 body"的一轮往返。
     *
     * 注意：成功路径不读 body，避免消费掉后面还要用的响应流。
     */
    private suspend fun HttpResponse.shouldHaveStatus(expected: HttpStatusCode) {
        if (status == expected) return
        fail("期望 $expected，实际 $status，响应体「${bodyAsText()}」")
    }

    /** JSON 数组按**结构化**比较，而不是比较原始字符串（前者不绑定空格与转义细节）。 */
    private fun String.asJsonStringList(): List<String> = Json.decodeFromString(this)

    /** 同上，用于对象型 body。 */
    private fun String.asJsonStringMap(): Map<String, String> = Json.decodeFromString(this)

    @Test
    fun `GET domains 返回域名列表`() = testApplication {
        val k8s = mockK8s(
            listOf(TestFixtures.hostData("a.example.com"), TestFixtures.hostData("b.example.com"))
        )
        application { easyDomain(config(), k8s) }

        client.get("/domains").apply {
            shouldHaveStatus(HttpStatusCode.OK)
            bodyAsText().asJsonStringList() shouldBe listOf("a.example.com", "b.example.com")
        }
    }

    /**
     * 契约守卫：新增成功的响应**不能**是 `text/plain` 的裸字符串。
     *
     * 这里曾经写的是 `call.respond(HttpStatusCode.Created, domain)`。`respond(String)` 不会走
     * ContentNegotiation 的 kotlinx 转换器，而是被 Ktor 内置的 `DefaultTextContentConverter`
     * 接走、发成 `Content-Type: text/plain`。前端的 `fetchBaseQuery` 默认按 JSON 解析非空
     * body，直接抛 SyntaxError 折算成 `PARSING_ERROR`；再叠上共享 `apiBase` 的
     * `retry(..., maxRetries = 1)`（默认重试条件**不看错误类型**），同一个 POST 被重发一次，
     * 第二次撞上"域名已存在"返回 400，界面于是表现成
     * 「201 之后又发一遍同样的请求，然后报错」。
     */
    @Test
    fun `新增成功的响应不带 text-plain 的 body`() = testApplication {
        val k8s = mockK8s()
        application { easyDomain(config(), k8s) }

        client.post("/domains/new.example.com").apply {
            shouldHaveStatus(HttpStatusCode.Created)
            withClue("text/plain 的 body 会让前端按 JSON 解析失败，并触发一次多余的 POST 重试") {
                contentType()?.withoutParameters() shouldNotBe ContentType.Text.Plain
            }
            bodyAsText() shouldBe ""
        }
    }

    @Test
    fun `POST domains 新增成功返回 201 并落地 ingress`() = testApplication {
        val k8s = mockK8s()
        application { easyDomain(config(), k8s) }

        client.post("/domains/new.example.com").shouldHaveStatus(HttpStatusCode.Created)

        val ingress = slot<Ingress>()
        verify(exactly = 1) { k8s.applyIngress(namespace, capture(ingress)) }
        withClue("ingress 名应由域名净化而来，否则多域名会互相覆盖") {
            ingress.captured.metadata.name shouldBe "new-example-com"
        }
    }

    @Test
    fun `POST 非法域名返回 400`() = testApplication {
        val k8s = mockK8s()
        application { easyDomain(config(), k8s) }

        client.post("/domains/bad_domain").shouldHaveStatus(HttpStatusCode.BadRequest)

        verify(exactly = 0) { k8s.applyIngress(any(), any()) }
    }

    @Test
    fun `POST 已存在域名返回 400`() = testApplication {
        val k8s = mockK8s(listOf(TestFixtures.hostData("taken.example.com")))
        application { easyDomain(config(), k8s) }

        client.post("/domains/taken.example.com").shouldHaveStatus(HttpStatusCode.BadRequest)
    }

    @Test
    fun `DELETE 删除成功返回 204，无此域名返回 404`() = testApplication {
        val k8s = mockK8s()
        every { k8s.removeIngressWithHost(namespace, "gone.example.com") } returns true
        application { easyDomain(config(), k8s) }

        client.delete("/domains/gone.example.com").shouldHaveStatus(HttpStatusCode.NoContent)
        client.delete("/domains/nothing.example.com").shouldHaveStatus(HttpStatusCode.NotFound)
    }

    @Test
    fun `GET 单个域名`() = testApplication {
        val k8s = mockK8s(listOf(TestFixtures.hostData("a.example.com")))
        application { easyDomain(config(), k8s) }

        client.get("/domains/a.example.com").apply {
            shouldHaveStatus(HttpStatusCode.OK)
            // 标量也要包一层对象：裸 String 会被发成 text/plain（见上面那条契约守卫）
            bodyAsText().asJsonStringMap() shouldBe mapOf("domain" to "a.example.com")
        }
        client.get("/domains/missing.example.com").shouldHaveStatus(HttpStatusCode.NotFound)
    }

    /**
     * 页面顶部"证书去向"那段说明的数据源。
     *
     * 断言两件事：字段齐（页面能拼出"传到哪"），以及**凭据不外泄** ——
     * 这条路由与 `/domains` 一样没有鉴权（公网可达），多带一个字节都是白送。
     */
    @Test
    fun `GET domains syncInfo 回显同步范围与证书去向，且不含凭据`() = testApplication {
        val k8s = mockK8s()
        application {
            easyDomain(configWithCertSync(listenerId = "lsn-test"), k8s, {
                mockk<AlbCertificateUploader>(relaxed = true).apply {
                    coEvery { loadBalancerId() } returns "alb-1"
                }
            })
        }

        val raw = client.get("/domains/syncInfo").apply {
            shouldHaveStatus(HttpStatusCode.OK)
        }.bodyAsText()

        Json.decodeFromString<DomainSyncInfo>(raw) shouldBe DomainSyncInfo(
            dnsCidrs = listOf("10.0.0.0/8", "100.64.0.0/10"),
            dnsCnameSuffixes = listOf("example.com"),
            aliyunRegion = "cn-hangzhou",
            aliyunAlbListenerId = "lsn-test",
            certSyncEnabled = true,
            aliyunLoadBalancerId = "alb-1"
        )
        raw shouldNotContain SECRET_ACCESS_KEY_ID
        raw shouldNotContain SECRET_ACCESS_KEY_SECRET
    }

    /**
     * 只配了账号、没配 ALB 监听时同步是关的。页面据此改说"未启用"，
     * 而不是拿一个不存在的监听去编故事。
     */
    @Test
    fun `syncInfo 在缺少 ALB 监听时 certSyncEnabled 为 false`() = testApplication {
        val k8s = mockK8s()
        application { easyDomain(configWithCertSync(listenerId = null), k8s) }

        val raw = client.get("/domains/syncInfo").apply {
            shouldHaveStatus(HttpStatusCode.OK)
        }.bodyAsText()

        Json.decodeFromString<DomainSyncInfo>(raw).apply {
            certSyncEnabled shouldBe false
            aliyunAlbListenerId shouldBe null
        }
    }

    @Test
    fun `未配置 namespace 时不挂路由`() = testApplication {
        application { easyDomain(EasyDomainConfig.fromEnv { null }, mockK8s()) }

        client.get("/domains").shouldHaveStatus(HttpStatusCode.NotFound)
    }

    /**
     * 证书同步的触发时机由宿主的调度任务决定："证书刚签发"才是它的输入，
     * "用户新增域名"不是——证书要等 cert-manager 走完 ACME 流程才存在。
     */
    @Test
    fun `新增域名不触发证书同步`() = testApplication {
        val k8s = mockK8s()
        application { easyDomain(configWithCertSync(), k8s) }

        client.post("/domains/new.example.com").shouldHaveStatus(HttpStatusCode.Created)

        verify(exactly = 0) { k8s.readIngressHostFromAllNamespaces() }
        verify(exactly = 0) { k8s.readStringSecret(any(), any()) }
    }
}
