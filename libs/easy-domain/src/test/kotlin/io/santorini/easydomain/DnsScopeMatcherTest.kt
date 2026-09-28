@file:Suppress("NonAsciiCharacters")

package io.santorini.easydomain

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DnsScopeMatcherTest {

    private class FakeDns(
        private val aRecords: Map<String, List<String>>,
        private val cnames: Map<String, String>,
    ) : DnsResolver {
        override fun resolveA(hostname: String): List<InetAddress> =
            aRecords[hostname].orEmpty().map { InetAddress.getByName(it) }

        override fun resolveCname(hostname: String): String? = cnames[hostname]
    }

    @Test
    fun `A 记录命中 CIDR 放行`() {
        val matcher = DnsScopeMatcher(
            listOf("10.0.0.0/8", "39.100.0.0/16"),
            emptyList(),
            FakeDns(mapOf("a.example.com" to listOf("10.1.2.3")), emptyMap())
        )
        assertTrue(matcher.matches("a.example.com"))
    }

    @Test
    fun `A 记录不在任何 CIDR 内不放行`() {
        val matcher = DnsScopeMatcher(
            listOf("10.0.0.0/8"),
            emptyList(),
            FakeDns(mapOf("a.example.com" to listOf("8.8.8.8")), emptyMap())
        )
        assertFalse(matcher.matches("a.example.com"))
    }

    @Test
    fun `CIDR 前缀边界判断正确`() {
        val matcher = DnsScopeMatcher(
            listOf("192.168.1.0/24"),
            emptyList(),
            FakeDns(
                mapOf(
                    "in.example.com" to listOf("192.168.1.255"),
                    "out.example.com" to listOf("192.168.2.1"),
                ), emptyMap()
            )
        )
        assertTrue(matcher.matches("in.example.com"))
        assertFalse(matcher.matches("out.example.com"))
    }

    @Test
    fun `CNAME 命中后缀放行`() {
        val matcher = DnsScopeMatcher(
            emptyList(),
            listOf("alb.example.com"),
            FakeDns(emptyMap(), mapOf("svc.example.com" to "xxx.alb.example.com"))
        )
        assertTrue(matcher.matches("svc.example.com"))
    }

    @Test
    fun `CNAME 后缀只接受子域或自身`() {
        val matcher = DnsScopeMatcher(
            emptyList(),
            listOf("alb.example.com"),
            FakeDns(
                emptyMap(),
                mapOf(
                    "good.example.com" to "alb.example.com",
                    "evil.example.com" to "evillb.example.com",
                )
            )
        )
        assertTrue(matcher.matches("good.example.com"))
        // evillb.example.com 虽然字符串上 endsWith("alb.example.com")，但不是合法子域
        assertFalse(matcher.matches("evil.example.com"))
    }

    @Test
    fun `A 与 CNAME 均未命中不放行`() {
        val matcher = DnsScopeMatcher(
            listOf("10.0.0.0/8"),
            listOf("alb.example.com"),
            FakeDns(mapOf("x.com" to listOf("1.2.3.4")), mapOf("x.com" to "elsewhere.com"))
        )
        assertFalse(matcher.matches("x.com"))
    }

    @Test
    fun `CIDR 记法非法时构造失败`() {
        val e = kotlin.runCatching {
            DnsScopeMatcher(listOf("10.0.0.0"), emptyList(), FakeDns(emptyMap(), emptyMap()))
        }.exceptionOrNull()
        assertEquals(true, e is IllegalArgumentException)
    }
}
