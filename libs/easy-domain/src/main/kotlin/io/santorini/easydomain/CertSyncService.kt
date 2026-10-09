package io.santorini.easydomain

import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.kubernetes.KubernetesClientService
import io.santorini.kubernetes.model.HostData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

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
 * 证书同步：把**全部 namespace** 内、DNS 落在预设范围、且 cert-manager 已签发的证书，
 * 上传到阿里云 ALB 证书池。
 *
 * 两点约定：
 * - **触发时机不由本接口负责**。K8s 侧没有便宜的"证书刚签发"订阅点（见 README），
 *   所以同步是纯拉取：由宿主决定何时调用（通常是定时任务）。
 * - 幂等靠 CAS 侧指纹比对：同一张证书（内容不变）只上传一次，本地无状态。
 *   因此定时重复调用是安全的，代价只是一次 ingress 列举 + 若干次证书比对。
 */
interface CertSyncService : Closeable {
    suspend fun syncEligibleCerts(): CertSyncReport
}

class CertSyncServiceImpl(
    private val kubernetesClientService: KubernetesClientService,
    private val matcher: DnsScopeMatcher,
    private val uploader: AlbCertificateUploader,
) : CertSyncService {

    override suspend fun syncEligibleCerts(): CertSyncReport = withContext(Dispatchers.IO) {
        val candidates = eligibleHosts()

        if (candidates.isEmpty()) {
            return@withContext CertSyncReport(emptyList(), emptyList(), emptyMap())
        }
        logger.info { "证书同步候选：${candidates.map { "${it.namespace}/${it.hostname}" }}" }

        val synced = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableMapOf<String, String>()

        for (host in candidates) {
            val domain = host.hostname
            val namespace = host.namespace!!
            val secretName = host.secretName!!
            try {
                val data = kubernetesClientService.readStringSecret(namespace, secretName)
                val certPem = data?.get("tls.crt")
                val keyPem = data?.get("tls.key")
                if (certPem.isNullOrBlank() || keyPem.isNullOrBlank()) {
                    failed[domain] = "secret $namespace/$secretName 缺少 tls.crt/tls.key"
                    continue
                }

                val fingerprint = certificateSha1Fingerprint(certPem)
                val existing = uploader.listUploadedFingerprints(domain)
                if (fingerprint in existing) {
                    logger.debug { "证书 $domain 已在证书池（指纹相同），跳过" }
                    skipped.add(domain)
                    continue
                }

                uploader.upload(certificateName(secretName, fingerprint), certPem, keyPem)
                synced.add(domain)
            } catch (e: Exception) {
                logger.error(e) { "同步 $namespace/$domain 的证书失败" }
                failed[domain] = e.message ?: e.javaClass.simpleName
            }
        }

        CertSyncReport(synced, skipped, failed)
    }

    /**
     * 选出参与同步的入口：有 cert-manager 签名、有 tls secret、DNS 命中预设范围。
     *
     * 跨 namespace 的同名 host 只保留一个（按 namespace、secretName 排序取首个）：
     * 一个 hostname 在集群里只会被一个 ingress 生效，上传两张不同的证书进同一个域名，
     * 只会让 ALB 侧变成随机命中。冲突必须告警而不是各传一份。
     */
    private fun eligibleHosts(): List<HostData> {
        val parsed = kubernetesClientService.readIngressHostFromAllNamespaces()
            // 没有证书的不参与同步
            .filter { it.issuerName != null && it.secretName != null }
            // 没有 namespace 就定位不到 secret
            .filter { it.namespace != null }

        val conflicts = parsed.groupBy { it.hostname }
            .filterValues { hosts -> hosts.mapNotNull { it.namespace }.distinct().size > 1 }
        if (conflicts.isNotEmpty()) {
            conflicts.forEach { (hostname, hosts) ->
                logger.warn {
                    "域名 $hostname 在多个 namespace 都有入口，只同步其中一个: " +
                            hosts.joinToString { "${it.namespace}/${it.secretName}" }
                }
            }
        }

        return parsed
            .groupBy { it.hostname }
            .map { (_, hosts) ->
                hosts.sortedWith(compareBy({ it.namespace }, { it.secretName })).first()
            }
            // DNS 必须解析到预设范围
            .filter { matcher.matches(it.hostname) }
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

    override fun close() {
        uploader.close()
    }
}
