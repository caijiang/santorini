package io.santorini.easydomain

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*

@Suppress("unused")
private val logger = KotlinLogging.logger {}

/**
 * 阿里云 ALB 证书池的上传抽象。
 *
 * ALB 自身不保存证书文件：第三方证书必须先上传到数字证书管理服务（CAS，cas 2020-04-07
 * 的 UploadUserCertificate），ALB 再从证书池引用——这是 ALB 官方唯一路径。
 */
interface AlbCertificateUploader {
    /**
     * @param domain 证书绑定的域名
     * @return 该域名已上传证书的指纹集合（SHA-256，大写 HEX，无分隔符）
     */
    fun listUploadedFingerprints(domain: String): Set<String>

    /**
     * 上传一张证书到证书池
     *
     * @return CAS 侧的证书 ID
     */
    fun upload(name: String, certPem: String, privateKeyPem: String): Long
}

/**
 * 基于官方 SDK [com.aliyun.cas20200407] 的实现。
 */
class CasCertificateUploader(
    accessKeyId: String,
    accessKeySecret: String,
    endpoint: String,
) : AlbCertificateUploader {
    private val logger = KotlinLogging.logger {}

    private val client: com.aliyun.cas20200407.Client by lazy {
        val config = com.aliyun.teaopenapi.models.Config()
            .setAccessKeyId(accessKeyId)
            .setAccessKeySecret(accessKeySecret)
        config.endpoint = endpoint
        com.aliyun.cas20200407.Client(config)
    }

    override fun listUploadedFingerprints(domain: String): Set<String> {
        val request = com.aliyun.cas20200407.models.ListUserCertificateOrderRequest()
            .setOrderType("UPLOAD")
            .setKeyword(domain)
            .setCurrentPage(1L)
            .setShowSize(100L)
        val body = client.listUserCertificateOrder(request).body ?: return emptySet()
        val fps = body.certificateOrderList.orEmpty()
            .mapNotNull { it.sha2?.uppercase() }
            .toSet()
        logger.debug { "CAS 上证书池中域名 $domain 已有 ${fps.size} 张证书" }
        return fps
    }

    override fun upload(name: String, certPem: String, privateKeyPem: String): Long {
        val request = com.aliyun.cas20200407.models.UploadUserCertificateRequest()
            .setName(name)
            .setCert(certPem)
            .setKey(privateKeyPem)
        val body = client.uploadUserCertificate(request).body
            ?: error("CAS UploadUserCertificate 无响应体")
        logger.info { "已上传证书 $name 到 CAS（certId=${body.certId}）" }
        return body.certId
    }
}

/**
 * 计算证书 PEM 的 SHA-256 指纹（大写 HEX，无分隔符），与 CAS 列表返回的 `Sha2` 对齐。
 */
fun certificateSha256Fingerprint(certPem: String): String {
    val base64 = certPem
        .replace("-----BEGIN CERTIFICATE-----", "")
        .replace("-----END CERTIFICATE-----", "")
        .replace("\\s".toRegex(), "")
    val der = Base64.getDecoder().decode(base64)
    val factory = CertificateFactory.getInstance("X.509")
    val cert = factory.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
    return digest.joinToString("") { "%02X".format(it) }
}
