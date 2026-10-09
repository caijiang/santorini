package io.santorini.easydomain

import io.santorini.easydomain.aliyun.AliyunNetworkDetector
import io.santorini.easydomain.aliyun.AliyunNetworkKind
import java.io.File

/**
 * easy-domain 的环境配置。
 *
 * 两个功能各自独立开闭：
 * - 域名管理：[namespace] 与 [ingressTemplate] 齐备 → [domainFeatureEnabled]
 * - 证书同步：阿里云 RAM 账号与 [aliyunRegion] 齐备 → [certSyncEnabled]
 *
 * 注意证书同步**不限定 namespace**：它扫描集群里所有 ingress，因此不需要 [namespace]。
 *
 * @param namespace         环境变量 `EASY_DOMAIN_NAMESPACE`，域名管理专用 namespace
 * @param ingressTemplate   渲染前的 Ingress YAML 模板正文，`{{domain}}` 为占位符
 * @param dnsCidrs          A 记录允许的 IP 段（CIDR），来自 `EASY_DOMAIN_DNS_CIDRS`，逗号分隔
 * @param dnsCnameSuffixes  CNAME 允许的目标后缀，来自 `EASY_DOMAIN_DNS_CNAME_SUFFIXES`，逗号分隔
 * @param aliyunAccessKeyId 环境变量 `EASY_DOMAIN_ALIYUN_ACCESS_KEY_ID`
 * @param aliyunAccessKeySecret 环境变量 `EASY_DOMAIN_ALIYUN_ACCESS_KEY_SECRET`
 * @param aliyunRegion    环境变量 `EASY_DOMAIN_ALIYUN_REGION`，如 `cn-hangzhou`
 * @param aliyunAlbListenerId 环境变量 `EASY_DOMAIN_ALIYUN_ALB_LISTENER_ID`
 * @param aliyunNetwork    环境变量 `EASY_DOMAIN_ALIYUN_NETWORK`，`auto`（默认）/`vpc`/`public`；
 *                         `auto` 表示按实例元数据自动探测（见 [AliyunNetworkDetector]）
 */
data class EasyDomainConfig(
    val namespace: String?,
    val ingressTemplate: String?,
    val dnsCidrs: List<String>,
    val dnsCnameSuffixes: List<String>,
    val aliyunAccessKeyId: String?,
    val aliyunAccessKeySecret: String?,
    val aliyunRegion: String?,
    val aliyunAlbListenerId: String?,
    val aliyunNetwork: AliyunNetworkKind?,
) {
    val domainFeatureEnabled: Boolean
        get() = !namespace.isNullOrBlank() && !ingressTemplate.isNullOrBlank()

    val certSyncEnabled: Boolean
        get() = !aliyunAccessKeyId.isNullOrBlank()
                && !aliyunAccessKeySecret.isNullOrBlank()
                && !aliyunRegion.isNullOrBlank()
                && !aliyunAlbListenerId.isNullOrBlank()

    /**
     * DNS 范围是否配了至少一条。
     *
     * 一条都没配时同步不会报错，但每个 host 都匹配不上——表现为静默空转，
     * 所以装配阶段要据此给出告警。
     */
    val dnsScopeConfigured: Boolean
        get() = dnsCidrs.isNotEmpty() || dnsCnameSuffixes.isNotEmpty()

    companion object {
        const val ENV_NAMESPACE = "EASY_DOMAIN_NAMESPACE"
        const val ENV_TEMPLATE = "EASY_DOMAIN_INGRESS_TEMPLATE"
        const val ENV_TEMPLATE_FILE = "EASY_DOMAIN_INGRESS_TEMPLATE_FILE"
        const val ENV_DNS_CIDRS = "EASY_DOMAIN_DNS_CIDRS"
        const val ENV_DNS_CNAME_SUFFIXES = "EASY_DOMAIN_DNS_CNAME_SUFFIXES"
        const val ENV_ALIYUN_ACCESS_KEY_ID = "EASY_DOMAIN_ALIYUN_ACCESS_KEY_ID"
        const val ENV_ALIYUN_ACCESS_KEY_SECRET = "EASY_DOMAIN_ALIYUN_ACCESS_KEY_SECRET"
        const val ENV_ALIYUN_REGION = "EASY_DOMAIN_ALIYUN_REGION"
        const val ENV_ALIYUN_ALB_LISTENER_ID = "EASY_DOMAIN_ALIYUN_ALB_LISTENER_ID"

        /** 显式指定接入点网络类型；不配则按实例元数据自动探测 */
        const val ENV_ALIYUN_NETWORK = AliyunNetworkKind.ENV_NETWORK

        /**
         * @param env 取环境变量的函数，默认系统环境；测试可注入
         * @throws IllegalStateException 模板正文与模板文件同时设置、或文件不可读时
         */
        fun fromEnv(env: (String) -> String? = System::getenv): EasyDomainConfig {
            val inline = env(ENV_TEMPLATE)?.takeIf { it.isNotBlank() }
            val templateFile = env(ENV_TEMPLATE_FILE)?.takeIf { it.isNotBlank() }
            val template = when {
                inline != null && templateFile != null ->
                    throw IllegalStateException("$ENV_TEMPLATE 与 $ENV_TEMPLATE_FILE 只能设置一个")

                inline != null -> inline
                templateFile != null -> File(templateFile).readText()
                else -> null
            }

            fun csv(key: String): List<String> =
                env(key)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

            return EasyDomainConfig(
                namespace = env(ENV_NAMESPACE)?.takeIf { it.isNotBlank() },
                ingressTemplate = template,
                dnsCidrs = csv(ENV_DNS_CIDRS),
                dnsCnameSuffixes = csv(ENV_DNS_CNAME_SUFFIXES),
                aliyunAccessKeyId = env(ENV_ALIYUN_ACCESS_KEY_ID)?.takeIf { it.isNotBlank() },
                aliyunAccessKeySecret = env(ENV_ALIYUN_ACCESS_KEY_SECRET)?.takeIf { it.isNotBlank() },
                aliyunRegion = env(ENV_ALIYUN_REGION)?.takeIf { it.isNotBlank() },
                aliyunAlbListenerId = env(ENV_ALIYUN_ALB_LISTENER_ID)?.takeIf { it.isNotBlank() },
                aliyunNetwork = AliyunNetworkKind.of(env(ENV_ALIYUN_NETWORK)),
            )
        }
    }
}
