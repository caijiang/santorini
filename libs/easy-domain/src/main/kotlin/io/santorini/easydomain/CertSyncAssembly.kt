package io.santorini.easydomain

import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.easydomain.aliyun.AliyunNetworkDetector
import io.santorini.easydomain.aliyun.AliyunNetworkKind
import io.santorini.kubernetes.KubernetesClientService

private val logger = KotlinLogging.logger {}

/**
 * 按配置装配证书同步服务；阿里云配置不全时返回 null。
 *
 * 接入点（公网 / VPC）在这里定：显式配置优先，否则按当前网络环境探测一次
 * （[AliyunNetworkDetector] 结果在进程内缓存，重复装配不会重复探测）。
 *
 * **触发时机由宿主决定**——本模块不监听"证书刚签发"这类事件（K8s 侧没有便宜的订阅点，
 * 详见 README），因此典型用法是把它交给调度任务：
 *
 * ```kotlin
 * val certSync = certSyncService(config, kubernetesClientService)   // 装配一次，复用
 * // 调度任务里周期性执行
 * runCatching { certSync?.syncEligibleCerts() }
 * ```
 *
 * 同步本身是幂等的（按证书指纹比对 CAS 侧），重复执行不会重复上传。
 * 扫描范围是**全部 namespace**，因此需要集群级 ingress / secret 读取权限。
 */
fun certSyncService(
    config: EasyDomainConfig,
    kubernetesClientService: KubernetesClientService,
): CertSyncService? {
    if (!config.certSyncEnabled) {
        logger.info { "easy-domain 证书同步未启用：需要阿里云 RAM 账号与 region" }
        return null
    }
    if (!config.dnsScopeConfigured) {
        logger.warn {
            "easy-domain 证书同步缺少 DNS 范围（${EasyDomainConfig.ENV_DNS_CIDRS} / " +
                    "${EasyDomainConfig.ENV_DNS_CNAME_SUFFIXES}），所有入口都不会命中，同步将空转"
        }
    }

    return CertSyncServiceImpl(
        kubernetesClientService,
        DnsScopeMatcher(config.dnsCidrs, config.dnsCnameSuffixes),
        config.toAlbCertificateUploader { region, kind ->
            logger.info {
                "easy-domain 证书同步装配完成：region=$region，接入点=${kind.name.lowercase()}，" +
                        "扫描全部 namespace"
            }
        }!!,
    )
}

fun EasyDomainConfig.toAlbCertificateUploader(
    otherWork: (String, AliyunNetworkKind)
    -> Unit = { _, _ -> }
): AlbCertificateUploader? {
    if (!certSyncEnabled) {
        return null
    }

    val region = aliyunRegion!!
    val networkKind = aliyunNetwork ?: AliyunNetworkDetector.detect().kindFor(region)
    otherWork(region, networkKind)
    return CasCertificateUploader(
        aliyunAccessKeyId!!,
        aliyunAccessKeySecret!!,
        region,
        aliyunAlbListenerId!!,
        networkKind,
    )
}