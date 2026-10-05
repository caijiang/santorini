package io.santorini.easydomain

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.santorini.easydomain.test.LocalAliyunConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlin.test.Test


private val json = Json {
    ignoreUnknownKeys = true
}

suspend fun workWithLocalAliyunConfig(
    javaClass: Class<Any>,
    block: suspend (LocalAliyunConfig) -> Unit
) {
    javaClass.getResourceAsStream("/local-build-aliyun-config.json")?.let { inputStream ->
        val data = inputStream.use {
            json.decodeFromStream<LocalAliyunConfig>(it)
        }
        block(data)
    }
}

/**
 * @author CJ
 */
class AlbCertificateUploaderTest {

    // openssl 生成的真实自签证书，CN=in-scope.example.com
    private val certPem = """
-----BEGIN CERTIFICATE-----
MIIFCDCCA/CgAwIBAgISBr+AkRwzwfY+KFhcb5neevazMA0GCSqGSIb3DQEBCwUA
MDMxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQwwCgYDVQQD
EwNZUjEwHhcNMjYxMDA1MDcyMjAwWhcNMjcwMTAzMDcyMTU5WjAiMSAwHgYDVQQD
ExdhZG1pbi5zaXQueHVueGkxNjg4LmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEP
ADCCAQoCggEBAKiDQUWiVovK6q9zKWcmce4USw2gBgJeKvBkvbwgncFBOMJzvRJK
vwKVubiKwTTICMDv8q/iJaLQrZoRcBSh1NXBpXiA8PDEXZJd0XXaF+gbkOrVOxgV
ekGbdpr8H1GCYtg7w7JZKXqYh8ihC3wg+XeY62LjC08mNcTPE3SzF7Uyn9RBfPDu
e/5wfavWaLuzCeisFdOGxqA+kMjrq4f/9UihsKr170t4+K3XHpRAMf9MRGGdlrDe
7zczu01LlCysyjbyLZMKbO9nNSnAknF9EsTbVS8aCnbJRxgyP1wmA7tJLQ4VHHp7
8bn5F02O5XNq2m7SZVacMpFboGFsY41a5rsCAwEAAaOCAiUwggIhMA4GA1UdDwEB
/wQEAwIFoDATBgNVHSUEDDAKBggrBgEFBQcDATAMBgNVHRMBAf8EAjAAMB0GA1Ud
DgQWBBTe4FWeO6eyb6sHJovT8aMFCMjKSTAfBgNVHSMEGDAWgBQfLzW+RhSCzUCx
rnksVXj699Ro+zAzBggrBgEFBQcBAQQnMCUwIwYIKwYBBQUHMAKGF2h0dHA6Ly95
cjEuaS5sZW5jci5vcmcvMCIGA1UdEQQbMBmCF2FkbWluLnNpdC54dW54aTE2ODgu
Y29tMBMGA1UdIAQMMAowCAYGZ4EMAQIBMC4GA1UdHwQnMCUwI6AhoB+GHWh0dHA6
Ly95cjEuYy5sZW5jci5vcmcvNTIuY3JsMIIBDAYKKwYBBAHWeQIEAgSB/QSB+gD4
AHYAYEyar3p/d18B1Ab8kg3ImesLHH34yVIb+voXdzuXi8kAAAGhCyZyFwAABAMA
RzBFAiEAylul6KwkMS5q7bv/YSVR0xE40z0M8up9gHeU5xhlDL4CICSQ4pPWHCAj
LdTOfGa3/QlGNrcfo0BIJUxumhtKEQ2YAH4AooEAGHNOF24dR+CVQPOBulRml81j
qENQcW64CU7a8Q0AAAGhCyZzJgAIAAAFAByzYeoEAwBHMEUCIQDu2pGiJYPr5PxG
h9XQERcPLvnPtrph6Ei9AWuLdxipVgIgKCA3reLR8QuJ3Z9Ze1xn39v5ZtOrqhHa
7VCCWhQXLeUwDQYJKoZIhvcNAQELBQADggEBACL/6IDqreKVwDcKZEpu5MVFOUMD
Jc9J08mCQSi9zLSlv8J8MquiYAIJ4LoKBLoKunz4GoZE0mLXcEsvuEpw0s5yOXh9
2boQQ18oeJ81Qa+Pu+X0hw/I5Arsf2ws+3+RTKbO2PNDFGLb9+haaf6yrahAC8ZO
L8CM0fv/z9GAXTCO2AyKDxepAsAU9w4bDQpLRqJTJibDlEY/VPtzgakjDDcaPuv5
HLrqYHbcxX9krYVlypoDwriqu0JmacJ4HyhjlGoGJcyd7//S2Z+WAvWbgIq7SAfw
0+8+I8MAoZYpxWEIcro5lSi44Mi4VjRzKN5w91/pQHv9vdQ4UPEdn3I5VWk=
-----END CERTIFICATE-----
-----BEGIN CERTIFICATE-----
MIIE2zCCAsOgAwIBAgIRAKICU/FfJpHAXcHOE7m8yk4wDQYJKoZIhvcNAQELBQAw
LjELMAkGA1UEBhMCVVMxDTALBgNVBAoTBElTUkcxEDAOBgNVBAMTB1Jvb3QgWVIw
HhcNMjUwOTAzMDAwMDAwWhcNMjgwOTAyMjM1OTU5WjAzMQswCQYDVQQGEwJVUzEW
MBQGA1UEChMNTGV0J3MgRW5jcnlwdDEMMAoGA1UEAxMDWVIxMIIBIjANBgkqhkiG
9w0BAQEFAAOCAQ8AMIIBCgKCAQEAoVi8X2xCYgMXvJxNPKp/oF13UMgmPABB07VC
LNDtoXmt9luEZNJSBV10VyT1Pz6LD8Zq1d2gc43WNl1AdRrj4sEnazbOiz0nPpmG
Bp2hui49oZtDIY6wdKeZAi5BbNU20CH6RSBBMLSQ9cXrH8dxdv4PAJ45ssGML68U
SE3BsjC2a6cAN9L5CgXVIQi5tfNiTPoFZZ3S0OlXqLmmtdV95udWAb5b6e/F49Di
CsH0Y00Ag72BVIb1hzynmKe+X0mERBTtsb3BwmpV9ipeBjMLoR/D9cHxHQCWoi5l
TmXwY015J5rGelz1nZjJuxc2kioaX29XJBnhMkP531rSdG5uMwIDAQABo4HuMIHr
MA4GA1UdDwEB/wQEAwIBhjATBgNVHSUEDDAKBggrBgEFBQcDATASBgNVHRMBAf8E
CDAGAQH/AgEAMB0GA1UdDgQWBBQfLzW+RhSCzUCxrnksVXj699Ro+zAfBgNVHSME
GDAWgBTe51tg0CJtQCh9Pw0B/qS1UrRRlDAyBggrBgEFBQcBAQQmMCQwIgYIKwYB
BQUHMAKGFmh0dHA6Ly95ci5pLmxlbmNyLm9yZy8wEwYDVR0gBAwwCjAIBgZngQwB
AgEwJwYDVR0fBCAwHjAcoBqgGIYWaHR0cDovL3lyLmMubGVuY3Iub3JnLzANBgkq
hkiG9w0BAQsFAAOCAgEA0+zvMq3kHig1ddTmmm+RibTr9/RpX7k4buanMMRqbV/y
IvP82zAHN3mvaw+cASuVsdpd0ikjhr4hnhJQLQOzOp2ccKrsdGOAgo0vddeISFAq
EWEV4lmUM3vFF796up+bSgmJ1u6RupDCMxDgF8M3eLvGuj6L0lu3zkQ0KuQLnKxL
tB0oQqn1Idg5CuuGpMvQzk29Pa3D/qHurc0EIM9SxukQuJqq63lxsYyRQFU8yMBO
hq1w5LbfaWNRrz1uklOfI/pYkAb2E2MTZrAMQkBIE2S8Jt1F8gRc96o/xOsrgvSk
a84AisX6xq1lz1Z7jGvrnXc4TMcjxZTjiTaihcYI1JIXZiLtEMSCa5l3cu8YWd6z
dLRQlqRdclVjuQfNHawRJ6GWlkK0QJosivTKwdBw3KxEtzGo8yMHERbsy57gP1UX
HOMcmZYQC0gtyR3SxfenIM/MxC3Ia2Ypab/kQ/CTnlIn2KQ5JUC6NYrGCbhFN9bp
5lKJStEwCUnLpntcrXk5XVDCNv/5RyWpRThkGOV7GetKkQ0qAY8hCzWK6oqnAhDZ
cjlYVdWfqOw3DIOX6EDNBgAqHarRVxyF9QZdOaXSyPJ0ueD2BYJEBgaCGQ8rAaU/
Qc123V5LTXDZW4CcsPBDyhy4v+c8hClAyw/IkJlfBqxB9D+/wvIMHgECZ4ptP6o=
-----END CERTIFICATE-----
-----BEGIN CERTIFICATE-----
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
-----END CERTIFICATE-----
    """.trimIndent()

    @Test
    fun certificateFingerprint() {
        certificateSha1Fingerprint(certPem) shouldBe "9C9EAE8CB9C9BC848735C13611886B0A4CBD4AA0"
    }

    @Test
    fun listUploadedFingerprints() = runTest {
        workWithLocalAliyunConfig(javaClass) {
            println(it)

            val uploader = CasCertificateUploader(it.accessKeyId, it.accessKeySecret, it.region, it.listenerId)

            val result = uploader.listUploadedFingerprints(it.domain)
            println("listUploadedFingerprints:$result")

            val result2 = uploader.upload("test-domain-${it.domain}", certPem, it.pk)
            println("upload:$result2")

            uploader.listUploadedFingerprints(it.domain) shouldHaveSize 1
        }
    }


}