package io.santorini.easydomain

import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.kubernetes.KubernetesClientService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

/**
 * 一次同步的清单
 */
data class CertSyncReport(
    /** 本次实际上传的域名 */
    val synced: List<String>,
    /** 证书池里已有、无需上传的域名 */
    val skipped: List<String>,
    /** 失败的域名 -> 原因 */
    val failed: Map<String, String>,
)

/**
 * 证书同步：把 namespace 内、DNS 落在预设范围、且 cert-manager 已签发的证书，
 * 上传到阿里云 ALB 证书池。
 *
 * 幂等靠 CAS 侧指纹比对：同一张证书（内容不变）只上传一次，无本地状态。
 */
interface CertSyncService {
    suspend fun syncEligibleCerts(): CertSyncReport
}

class CertSyncServiceImpl(
    private val kubernetesClientService: KubernetesClientService,
    private val namespace: String,
    private val matcher: DnsScopeMatcher,
    private val uploader: AlbCertificateUploader,
) : CertSyncService {

    override suspend fun syncEligibleCerts(): CertSyncReport = withContext(Dispatchers.IO) {
        val candidates = kubernetesClientService.readIngressHostFromNamespace(namespace)
            // 没有证书的不参与同步
            .filter { it.issuerName != null && it.secretName != null }
            // DNS 必须解析到预设范围
            .filter { matcher.matches(it.hostname) }

        if (candidates.isEmpty()) {
            return@withContext CertSyncReport(emptyList(), emptyList(), emptyMap())
        }
        logger.info { "证书同步候选：${candidates.map { it.hostname }}" }

        val synced = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableMapOf<String, String>()

        for (host in candidates) {
            val domain = host.hostname
            val secretName = host.secretName!!
            try {
                val data = kubernetesClientService.readStringSecret(namespace, secretName)
                val certPem = data?.get("tls.crt")
                val keyPem = data?.get("tls.key")
                if (certPem.isNullOrBlank() || keyPem.isNullOrBlank()) {
                    failed[domain] = "secret $secretName 缺少 tls.crt/tls.key"
                    continue
                }

                val fingerprint = certificateSha256Fingerprint(certPem)
                val existing = uploader.listUploadedFingerprints(domain)
                if (fingerprint in existing) {
                    logger.debug { "证书 $domain 已在证书池（指纹相同），跳过" }
                    skipped.add(domain)
                    continue
                }

                uploader.upload(certificateName(secretName, fingerprint), certPem, keyPem)
                synced.add(domain)
            } catch (e: Exception) {
                logger.error(e) { "同步 $domain 的证书失败" }
                failed[domain] = e.message ?: e.javaClass.simpleName
            }
        }

        CertSyncReport(synced, skipped, failed)
    }

    /**
     * CAS 证书名要求账号内唯一且不超过 63 字符：
     * `santorini-<secretName>-<指纹前 12 位>`，同一张证书重复上传天然同名。
     */
    private fun certificateName(secretName: String, fingerprint: String): String {
        val fp = fingerprint.take(12)
        val maxSecretLen = 63 - "santorini-".length - fp.length - 1
        val secret = if (secretName.length > maxSecretLen) secretName.take(maxSecretLen) else secretName
        return "santorini-$secret-$fp"
    }
}
