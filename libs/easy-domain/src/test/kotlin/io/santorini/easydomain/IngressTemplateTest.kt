@file:Suppress("NonAsciiCharacters", "RemoveRedundantBackticks")

package io.santorini.easydomain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IngressTemplateTest {

    @Test
    fun `渲染后 rules 与 tls 都替换为目标域名`() {
        val template = IngressTemplate(TestFixtures.TEMPLATE_YAML)
        val domain = "a.example.com"
        val ingress = template.render(domain)

        assertEquals(domain, ingress.spec.rules[0].host)
        assertEquals(domain, ingress.spec.tls[0].hosts[0])
        assertEquals("tls-a-example-com", ingress.spec.tls[0].secretName)
        assertEquals("letsencrypt", ingress.metadata.annotations["cert-manager.io/cluster-issuer"])
    }

    @Test
    fun `资源名由域名净化而来`() {
        val template = IngressTemplate(TestFixtures.TEMPLATE_YAML)
        assertEquals("a-example-com", template.render("a.example.com").metadata.name)
        assertEquals("wildcard-example-com", template.render("*.example.com").metadata.name)
    }

    @Test
    fun `同域名重复渲染得到同名资源，幂等`() {
        val template = IngressTemplate(TestFixtures.TEMPLATE_YAML)
        assertEquals(
            template.render("x.y.com").metadata.name,
            template.render("x.y.com").metadata.name
        )
    }

    @Test
    fun `非法 YAML 被拒绝`() {
        // fabric8 底层是 Jackson 解析，异常类型不保证，只验证会抛
        assertFailsWith<Exception> {
            IngressTemplate("not: [valid: yaml")
        }
    }

    @Test
    fun `无占位符但有静态规则的模板可以构造，但渲染被拒绝`() {
        val staticTemplate = IngressTemplate(
            """
            apiVersion: networking.k8s.io/v1
            kind: Ingress
            metadata:
              name: x
            spec:
              rules:
                - host: "static.example.com"
                  http:
                    paths: []
            """.trimIndent()
        )
        val e = assertFailsWith<IllegalArgumentException> { staticTemplate.render("other.example.com") }
        assertTrue(e.message!!.contains("占位符"))
    }

    @Test
    fun `空规则且无占位符的模板被拒绝`() {
        assertFailsWith<IllegalArgumentException> {
            IngressTemplate(
                """
                apiVersion: networking.k8s.io/v1
                kind: Ingress
                metadata:
                  name: x
                spec:
                  rules: []
                """.trimIndent()
            )
        }
    }
}
