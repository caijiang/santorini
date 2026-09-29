@file:Suppress("NonAsciiCharacters", "RemoveRedundantBackticks")

package io.santorini.easydomain

import io.fabric8.kubernetes.api.model.networking.v1.Ingress
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.santorini.kubernetes.KubernetesClientService
import io.santorini.kubernetes.model.HostData
import kotlinx.serialization.json.Json
import kotlin.test.Test

class EasyDomainRoutesTest {

    private val namespace = "easy-domains"

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

    /** 阿里云配置齐全的配置：证书同步已启用，但它与路由无关 */
    private fun configWithCertSync() = EasyDomainConfig.fromEnv { key ->
        when (key) {
            EasyDomainConfig.ENV_NAMESPACE -> namespace
            EasyDomainConfig.ENV_TEMPLATE -> TestFixtures.TEMPLATE_YAML
            EasyDomainConfig.ENV_ALIYUN_ACCESS_KEY_ID -> "id"
            EasyDomainConfig.ENV_ALIYUN_ACCESS_KEY_SECRET -> "secret"
            EasyDomainConfig.ENV_ALIYUN_ENDPOINT -> "cas.aliyuncs.com"
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

    @Test
    fun `POST domains 新增成功返回 201 并落地 ingress`() = testApplication {
        val k8s = mockK8s()
        application { easyDomain(config(), k8s) }

        client.post("/domains/new.example.com").apply {
            shouldHaveStatus(HttpStatusCode.Created)
            bodyAsText() shouldBe "new.example.com"
        }

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
            bodyAsText() shouldBe "a.example.com"
        }
        client.get("/domains/missing.example.com").shouldHaveStatus(HttpStatusCode.NotFound)
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
