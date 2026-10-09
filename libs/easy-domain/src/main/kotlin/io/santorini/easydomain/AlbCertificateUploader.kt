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
import darabonba.core.EndpointType
import darabonba.core.client.ClientOverrideConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.easydomain.aliyun.*
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
interface AlbCertificateUploader : Closeable {
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
 *
 * ALB 与 CAS 两个 client 都按 [networkKind] 选接入点（`alb-vpc.*` / `cas-vpc.*` 或各自的公网接入点），
 * 且两者的实现机制不同，原因写在两处 `by lazy` 的注释里。
 *
 * 关联 api有:
 * - https://next.api.aliyun.com/document/Alb/2020-06-16/ListListenerCertificates
 * - https://next.api.aliyun.com/api/cas/2020-04-07/GetUserCertificateDetail
 * - https://next.api.aliyun.com/api/cas/2020-04-07/ListUserCertificateOrder
 * - https://next.api.aliyun.com/api/cas/2020-04-07/UploadUserCertificate
 * - https://next.api.aliyun.com/document/Alb/2020-06-16/AssociateAdditionalCertificatesWithListener
 */
class CasCertificateUploader(
    accessKeyId: String,
    accessKeySecret: String,
    private val region: String,
    private val listenerId: String,
    /**
     * 走公网接入点还是 VPC 接入点。
     *
     * 由装配方按当前网络环境决定（见 [io.santorini.easydomain.certSyncService]），默认公网。
     * 选错是连不上，不是慢——所以这个值不接受"猜"，只接受探测结果或显式配置。
     */
    private val networkKind: AliyunNetworkKind = AliyunNetworkKind.PUBLIC,
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

    private val vpc = networkKind == AliyunNetworkKind.VPC

    private val albClient by lazy {
        val builder = AsyncClient.builder()
            .region(region)
            .credentialsProvider(provider)
        if (vpc) {
            // 交给 SDK 按官方规则拼 `alb-vpc.<region>.aliyuncs.com`：
            // 它自带 endpointMap 例外（如华东 1 金融云 cn-hangzhou-finance 没有 VPC 接入点，
            // 会退回公网），比我们自己抄一份表可靠。
            builder.overrideConfiguration(
                ClientOverrideConfiguration.create().setEndpointType(EndpointType.VPC)
            )
        }
        builder.build()
    }

    private val casClient by lazy {
        val builder = com.aliyun.sdk.service.cas20200407.AsyncClient.builder()
            .region(region)
            .credentialsProvider(provider)
        if (vpc) {
            // CAS 只能显式覆盖：它内置的 endpointMap 把**所有** region 都指向公网的
            // `cas.aliyuncs.com`，而 endpointMap 的优先级高于 endpointType，
            // 光设 endpointType 是无效的。
            builder.overrideConfiguration(
                ClientOverrideConfiguration.create().setEndpointOverride(casVpcEndpoint(region))
            )
        }
        builder.build()
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
