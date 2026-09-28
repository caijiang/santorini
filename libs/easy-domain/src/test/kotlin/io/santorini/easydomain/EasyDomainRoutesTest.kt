@file:Suppress("NonAsciiCharacters")

package io.santorini.easydomain

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
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun `GET domains 返回域名列表`() = testApplication {
        val k8s = mockK8s(
            listOf(TestFixtures.hostData("a.example.com"), TestFixtures.hostData("b.example.com"))
        )
        application { easyDomain(config(), k8s) }

        val response = client.get("/domains")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""["a.example.com","b.example.com"]""", response.bodyAsText())
    }

    @Test
    fun `POST domains 新增成功返回 201 并落地 ingress`() = testApplication {
        val k8s = mockK8s()
        application { easyDomain(config(), k8s) }

        val response = client.post("/domains/new.example.com")

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("new.example.com", response.bodyAsText())
        val slot = slot<io.fabric8.kubernetes.api.model.networking.v1.Ingress>()
        verify(exactly = 1) { k8s.applyIngress(namespace, capture(slot)) }
        assertEquals("new-example-com", slot.captured.metadata.name)
    }

    @Test
    fun `POST 非法域名返回 400`() = testApplication {
        val k8s = mockK8s()
        application { easyDomain(config(), k8s) }

        val response = client.post("/domains/bad_domain")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        verify(exactly = 0) { k8s.applyIngress(any(), any()) }
    }

    @Test
    fun `POST 已存在域名返回 400`() = testApplication {
        val k8s = mockK8s(listOf(TestFixtures.hostData("taken.example.com")))
        application { easyDomain(config(), k8s) }

        val response = client.post("/domains/taken.example.com")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE 删除成功返回 204，无此域名返回 404`() = testApplication {
        val k8s = mockK8s()
        every { k8s.removeIngressWithHost(namespace, "gone.example.com") } returns true
        application { easyDomain(config(), k8s) }

        assertEquals(HttpStatusCode.NoContent, client.delete("/domains/gone.example.com").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/domains/nothing.example.com").status)
    }

    @Test
    fun `GET 单个域名`() = testApplication {
        val k8s = mockK8s(listOf(TestFixtures.hostData("a.example.com")))
        application { easyDomain(config(), k8s) }

        assertEquals(HttpStatusCode.OK, client.get("/domains/a.example.com").status)
        assertEquals("a.example.com", client.get("/domains/a.example.com").bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/domains/missing.example.com").status)
    }

    @Test
    fun `未配置 namespace 时不挂路由`() = testApplication {
        application { easyDomain(EasyDomainConfig.fromEnv { null }, mockK8s()) }

        assertEquals(HttpStatusCode.NotFound, client.get("/domains").status)
    }
}
