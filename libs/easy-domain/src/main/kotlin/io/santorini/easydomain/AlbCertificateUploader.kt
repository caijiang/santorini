package io.santorini.easydomain

import com.aliyun.auth.credentials.Credential
import com.aliyun.auth.credentials.ICredential
import com.aliyun.auth.credentials.provider.DefaultCredentialProvider
import com.aliyun.auth.credentials.provider.ICredentialProvider
import com.aliyun.sdk.service.alb20200616.AsyncClient
import com.aliyun.sdk.service.alb20200616.models.AssociateAdditionalCertificatesWithListenerRequest
import com.aliyun.sdk.service.alb20200616.models.ListListenerCertificatesRequest
import com.aliyun.sdk.service.cas20200407.models.ListUserCertificateOrderRequest
import com.aliyun.sdk.service.cas20200407.models.UploadUserCertificateRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.easydomain.aliyun.listAllListenerCertificates
import io.santorini.easydomain.aliyun.listAllUserCertificateOrder
import io.santorini.easydomain.aliyun.supportDomain
import io.santorini.easydomain.aliyun.toUserCertificateDetailRequest
import kotlinx.coroutines.future.await
import java.io.Closeable
import java.security.MessageDigest
import java.security.cert.CertificateFactory

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
    suspend fun listUploadedFingerprints(domain: String): Set<String>

    /**
     * 上传一张证书到证书池
     *
     * @return CAS 侧的证书 ID
     */
    suspend fun upload(name: String, certPem: String, privateKeyPem: String): Long
}

/**
 * 基于官方 SDK [com.aliyun] 的实现。
 */
class CasCertificateUploader(
    accessKeyId: String,
    accessKeySecret: String,
    private val region: String,
    private val listenerId: String
) : AlbCertificateUploader, Closeable {

    private val provider = DefaultCredentialProvider.builder()
        .addCustomizeProviders(
            object : ICredentialProvider {
                override fun getCredentials(): ICredential {
                    return Credential.builder()
                        .accessKeyId(accessKeyId)
                        .accessKeySecret(accessKeySecret)
                        .build()
                }

                override fun close() {
                }
            }
        )
        .build()
    private val albClient by lazy {
        AsyncClient.builder()
            .region(region)
            .credentialsProvider(provider)
            .build()
    }

    private val casClient by lazy {
        com.aliyun.sdk.service.cas20200407.AsyncClient.builder()
            .region(region)
            .credentialsProvider(provider)
            .build()
    }

    override suspend fun listUploadedFingerprints(domain: String): Set<String> {
        // 要确保即在 alb 也在 cas
        val certsInAlb = albClient.listAllListenerCertificates(
            ListListenerCertificatesRequest.builder()
                .certificateType("Server")
                .listenerId(listenerId)
        )

        logger.debug { "ALB $listenerId 上证书${certsInAlb.size}张证书" }

        return certsInAlb.mapNotNull {
            val x = casClient.getUserCertificateDetail(it.toUserCertificateDetailRequest())
                .await().body
            if (x.supportDomain(domain)) {
                x.fingerprint
            } else
                null
        }.toSet()
    }

    override suspend fun upload(name: String, certPem: String, privateKeyPem: String): Long {
        // 上传要稍微复杂一些, 先在 cas 上过一道(幂等),然后再在 alb 上过一道.
        val fingerprint = certificateSha1Fingerprint(certPem)

        val casCurrent = casClient.listAllUserCertificateOrder(
            ListUserCertificateOrderRequest.builder()
                .orderType("CERT")
        )

        val id = casCurrent.find {
            it.fingerprint.equals(fingerprint, ignoreCase = true)
        }?.certificateId ?: run {
            logger.debug { "目前没有适配的证书，上传之" }

            val result = casClient.uploadUserCertificate(
                UploadUserCertificateRequest.builder()
                    .name(name)
                    .cert(certPem)
                    .key(privateKeyPem)
                    .clientToken("upload-$name")
                    .build()
            ).await().body

            logger.info {
                "cas上传证书: ${result.requestId} ${result.certId}"
            }
            result.certId
        }

        logger.debug { "计划分配证书${id}" }

        val result = albClient.associateAdditionalCertificatesWithListener(
            AssociateAdditionalCertificatesWithListenerRequest.builder()
                .listenerId(listenerId)
                .certificates(
                    listOf(
                        AssociateAdditionalCertificatesWithListenerRequest.Certificates.builder()
                            .certificateId("$id-$region")
                            .build()
                    )
                )
                .build()
        ).await()

        logger.info {
            "ALB分配证书结果:${result.body.requestId} ${result.body.jobId}"
        }

        return id
    }

    override fun close() {
        albClient.close()
        casClient.close()
    }
}

/**
 * 计算证书 PEM 的 SHA-256 指纹（大写 HEX，无分隔符），与 CAS 列表返回的 `Sha2` 对齐。
 */
fun certificateSha256Fingerprint(certPem: String): String {
    val leaf = CertificateFactory.getInstance("X.509")
        .generateCertificates(certPem.byteInputStream())
        .first()
    return MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
        .joinToString("") { "%02X".format(it) }
}

fun certificateSha1Fingerprint(certPem: String): String {
    val leaf = CertificateFactory.getInstance("X.509")
        .generateCertificates(certPem.byteInputStream())
        .first()
    return MessageDigest.getInstance("SHA-1").digest(leaf.encoded)
        .joinToString("") { "%02X".format(it) }
}
