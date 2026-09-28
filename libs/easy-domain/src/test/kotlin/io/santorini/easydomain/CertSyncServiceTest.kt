@file:Suppress("NonAsciiCharacters", "RemoveRedundantBackticks")

package io.santorini.easydomain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.santorini.kubernetes.KubernetesClientService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CertSyncServiceTest {

    private val namespace = "easy-domains"

    // openssl 生成的真实自签证书，CN=in-scope.example.com
    private val certPem = """
        -----BEGIN CERTIFICATE-----
        MIIDUDCCAjigAwIBAgIUaiCZIwUKHaK/FKusHuyWEUrkBuswDQYJKoZIhvcNAQEL
        BQAwHzEdMBsGA1UEAwwUaW4tc2NvcGUuZXhhbXBsZS5jb20wIBcNMjYwOTI4MTIx
        MDA1WhgPMjEyNjA5MDQxMjEwMDVaMB8xHTAbBgNVBAMMFGluLXNjb3BlLmV4YW1w
        bGUuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy4tsWygfHZrN
        i7BmYqgwE29O6nX++CnO8WeClNO3DsOlfSIUiK035e+kIVb3PnWRSobEfEZpl4xn
        699JeZKpKJcu7SvlHb+pgUH2l5O85po0dODTe/kgyVfrc7bxH+YjGgcziK7AyQqF
        UFo/mkES6ghk44kBp+2xEEetIyeLGDkPJpTNXVwNUF7agDDEM2k6R+lt+ZtVKYwT
        K6N/Kj+D973/8lYWSPAwxzGK174oID4C7VLjnwTujiJy0sfd+Hg6NI768jsnbuuf
        oN8kzl5/6hCPBjAuSaAQqWgJYhZMP1DSUMZ2IWmHJtDnJyuRJepGMoRvBaPYuEs1
        7QwvO31OQQIDAQABo4GBMH8wHQYDVR0OBBYEFK1BPCRN1Uo7ZqLGOfFVpTZL7Nmc
        MB8GA1UdIwQYMBaAFK1BPCRN1Uo7ZqLGOfFVpTZL7NmcMA8GA1UdEwEB/wQFMAMB
        Af8wLAYDVR0RBCUwI4IUaW4tc2NvcGUuZXhhbXBsZS5jb22CC2V4YW1wbGUuY29t
        MA0GCSqGSIb3DQEBCwUAA4IBAQBBRWRX/prsxmD+bmFkXEXhBTRodIROTph0NiQX
        x5i+gc5BZsEbFhjZ2VI42apmqTpS7PiBlIFDFmpavnUjORoLCcDRSr20hH60rtaq
        flWMb/7XCiGkgMQtq2F+Gt/6eH5+TWGWDaS7oG6uD1sxjuG6y7FxCeMYsiXimSI5
        0lbEqObYF/gdF1WWAsdlxgbINVXvNSe3OPPTRdFUx+eZy85F7PZC2ugtlBnX3oPg
        KzIzfAYiq9X5jBfF/7eQ2XIMWULZPvxx9QxymmPl6Ucca7AQVpTI8t/T3Wlo5JOO
        kAkWH7wGaBjZjhNm2OjryZLtC4A3zSc+9fL031LJD8Xzme9h
        -----END CERTIFICATE-----
    """.trimIndent()

    // 私钥内容不参与任何解析，只要形状正确
    private val keyPem = """
        -----BEGIN PRIVATE KEY-----
        MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC=
        -----END PRIVATE KEY-----
    """.trimIndent()

    private fun secretData(cert: String = certPem, key: String = keyPem): Map<String, String> =
        mapOf("tls.crt" to cert, "tls.key" to key)

    private fun fakeUploader(existing: Set<String> = emptySet()): Pair<AlbCertificateUploader, MutableList<String>> {
        val uploaded = mutableListOf<String>()
        val uploader = object : AlbCertificateUploader {
            override fun listUploadedFingerprints(domain: String): Set<String> = existing
            override fun upload(name: String, certPem: String, privateKeyPem: String): Long {
                uploaded.add(name)
                return 1L
            }
        }
        return uploader to uploaded
    }

    private fun mockK8s(
        hosts: List<String>,
        secrets: Map<String, Map<String, String>> = emptyMap(),
    ): KubernetesClientService {
        val k8s = mockk<KubernetesClientService>()
        every { k8s.readIngressHostFromNamespace(namespace) } returns
                hosts.map { TestFixtures.hostData(it) }
        hosts.forEach { host ->
            val secretName = host.replace(".", "-")
            every { k8s.readStringSecret(namespace, secretName) } returns secrets[secretName]
        }
        return k8s
    }

    /** 假 DNS：cname 非 null 时所有域名都解析到这个 CNAME */
    private class FakeDns(private val cname: String?) : DnsResolver {
        override fun resolveA(hostname: String): List<java.net.InetAddress> = emptyList()
        override fun resolveCname(hostname: String): String? = cname
    }

    private fun service(
        k8s: KubernetesClientService,
        uploader: AlbCertificateUploader,
        cnameSuffixes: List<String> = listOf("scope.example.com"),
        dns: DnsResolver = FakeDns("x.scope.example.com"),
    ) = CertSyncServiceImpl(k8s, namespace, DnsScopeMatcher(listOf(), cnameSuffixes, dns), uploader)

    @Test
    fun `证书指纹计算与 openssl sha256 对齐`() {
        assertEquals(
            "7ED81906BC40E39CC7E14B3FFBF42D0F4237C084EF1C8218D6B6B0F607EEB139",
            certificateSha256Fingerprint(certPem)
        )
    }

    @Test
    fun `DNS 命中的域名被同步`() = runTest {
        val k8s = mockK8s(
            hosts = listOf("in-scope.example.com"),
            secrets = mapOf("in-scope-example-com" to secretData()),
        )
        val (uploader, uploaded) = fakeUploader()
        val sync = service(k8s, uploader)

        val report = sync.syncEligibleCerts()

        assertEquals(listOf("in-scope.example.com"), report.synced)
        assertEquals(1, uploaded.size)
        assertTrue(uploaded[0].startsWith("santorini-in-scope-example-com-"))
        verify { k8s.readStringSecret(namespace, "in-scope-example-com") }
    }

    @Test
    fun `指纹已在证书池的跳过`() = runTest {
        val fingerprint = certificateSha256Fingerprint(certPem)
        val k8s = mockK8s(
            hosts = listOf("in-scope.example.com"),
            secrets = mapOf("in-scope-example-com" to secretData()),
        )
        val (uploader, uploaded) = fakeUploader(setOf(fingerprint))
        val sync = service(k8s, uploader)

        val report = sync.syncEligibleCerts()

        assertEquals(listOf("in-scope.example.com"), report.skipped)
        assertTrue(report.synced.isEmpty())
        assertTrue(uploaded.isEmpty())
    }

    @Test
    fun `DNS 不命中的域名不参与同步`() = runTest {
        val k8s = mockK8s(hosts = listOf("out-of-scope.example.com"))
        val (uploader, uploaded) = fakeUploader()
        val sync = service(k8s, uploader, dns = FakeDns(null))

        val report = sync.syncEligibleCerts()

        assertTrue(report.synced.isEmpty())
        assertTrue(uploaded.isEmpty())
        verify(exactly = 0) { k8s.readStringSecret(any(), any()) }
    }

    @Test
    fun `secret 缺失或内容非法记为失败而不是抛出`() = runTest {
        val k8s = mockK8s(
            hosts = listOf("no-secret.example.com", "bad-secret.example.com"),
            secrets = mapOf(
                "no-secret-example-com" to emptyMap(),
                "bad-secret-example-com" to mapOf("tls.crt" to "not-a-cert", "tls.key" to keyPem),
            ),
        )
        val (uploader, uploaded) = fakeUploader()
        // example.com 后缀放行所有候选
        val sync = service(k8s, uploader, cnameSuffixes = listOf("example.com"))

        val report = sync.syncEligibleCerts()

        assertEquals(2, report.failed.size)
        assertTrue(uploaded.isEmpty())
        assertTrue(report.failed.keys.containsAll(listOf("no-secret.example.com", "bad-secret.example.com")))
    }

    @Test
    fun `证书名包含指纹，同一张证书天然同名`() = runTest {
        val k8s = mockK8s(
            hosts = listOf("in-scope.example.com"),
            secrets = mapOf("in-scope-example-com" to secretData()),
        )
        val uploaded = mutableListOf<String>()
        val uploader = object : AlbCertificateUploader {
            override fun listUploadedFingerprints(domain: String): Set<String> = emptySet()
            override fun upload(name: String, certPem: String, privateKeyPem: String): Long {
                uploaded.add(name)
                return 1L
            }
        }
        val sync = service(k8s, uploader)

        sync.syncEligibleCerts()
        sync.syncEligibleCerts()

        val slotName = uploaded.distinct()
        assertEquals(2, uploaded.size)
        assertEquals(1, slotName.size, "同一证书两次同步应得到同一证书名")
    }
}
