@file:Suppress("NonAsciiCharacters")

package io.santorini.easydomain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.santorini.kubernetes.KubernetesClientService
import io.santorini.kubernetes.model.HostData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DomainServiceTest {

    private val namespace = "easy-domains"
    private val template = IngressTemplate(TestFixtures.TEMPLATE_YAML)

    private fun mockK8s(existing: List<HostData> = emptyList()): KubernetesClientService {
        val k8s = mockk<KubernetesClientService>()
        every { k8s.readIngressHostFromNamespace(namespace) } returns existing
        every { k8s.applyIngress(namespace, any()) } returns Unit
        every { k8s.removeIngressWithHost(namespace, any()) } returns false
        return k8s
    }

    @Test
    fun `listDomains 来自 ingress 读取`() {
        val k8s = mockK8s(
            listOf(
                TestFixtures.hostData("a.example.com"),
                TestFixtures.hostData("b.example.com"),
            )
        )
        val service = DomainServiceImpl(k8s, namespace, template)
        assertEquals(listOf("a.example.com", "b.example.com"), service.listDomains())
    }

    @Test
    fun `addDomain 按模板渲染并落地`() {
        val k8s = mockK8s()
        val service = DomainServiceImpl(k8s, namespace, template)

        service.addDomain("new.example.com")

        val slot = slot<io.fabric8.kubernetes.api.model.networking.v1.Ingress>()
        verify(exactly = 1) { k8s.applyIngress(namespace, capture(slot)) }
        assertEquals("new.example.com", slot.captured.spec.rules[0].host)
        assertEquals("new-example-com", slot.captured.metadata.name)
        assertEquals("tls-new-example-com", slot.captured.spec.tls[0].secretName)
    }

    @Test
    fun `addDomain 拒绝已存在的域名`() {
        val k8s = mockK8s(listOf(TestFixtures.hostData("taken.example.com")))
        val service = DomainServiceImpl(k8s, namespace, template)

        val e = assertFailsWith<IllegalArgumentException> { service.addDomain("taken.example.com") }
        assertTrue(e.message!!.contains("已存在"))
        verify(exactly = 0) { k8s.applyIngress(any(), any()) }
    }

    @Test
    fun `addDomain 拒绝非法域名`() {
        val k8s = mockK8s()
        val service = DomainServiceImpl(k8s, namespace, template)

        listOf("no spaces.com", "-leading.com", "a..b.com", "例子.com").forEach { bad ->
            assertFailsWith<IllegalArgumentException> { service.addDomain(bad) }
        }
        verify(exactly = 0) { k8s.applyIngress(any(), any()) }
    }

    @Test
    fun `removeDomain 透传删除结果`() {
        val k8s = mockK8s()
        every { k8s.removeIngressWithHost(namespace, "gone.example.com") } returns true
        val service = DomainServiceImpl(k8s, namespace, template)

        assertTrue(service.removeDomain("gone.example.com"))
        assertTrue(!service.removeDomain("nothing.example.com"))
    }
}
