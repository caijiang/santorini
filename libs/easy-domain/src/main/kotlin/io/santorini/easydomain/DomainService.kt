package io.santorini.easydomain

import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.kubernetes.KubernetesClientService

private val logger = KotlinLogging.logger {}

/**
 * 域名管理。对外只有 domain 字符串——Ingress 细节全部被模板吸收。
 */
interface DomainService {
    /**
     * @return 当前 namespace 下所有已配置的域名
     */
    fun listDomains(): List<String>

    /**
     * 按模板新增一个域名
     *
     * @throws IllegalArgumentException 域名不合法、或已存在
     */
    fun addDomain(domain: String)

    /**
     * 删除域名（删除 rule host 匹配的 Ingress）
     *
     * @return 是否真的删除了
     */
    fun removeDomain(domain: String): Boolean

    fun exists(domain: String): Boolean
}

private val HOSTNAME_REGEX = Regex(
    "^(\\*\\.)?([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,63}$"
)

fun validateDomain(domain: String) {
    require(domain.length <= 253) { "域名过长: $domain" }
    require(HOSTNAME_REGEX.matches(domain)) { "非法域名: $domain" }
}

class DomainServiceImpl(
    private val kubernetesClientService: KubernetesClientService,
    private val namespace: String,
    private val template: IngressTemplate,
) : DomainService {

    override fun listDomains(): List<String> =
        kubernetesClientService.readIngressHostFromNamespace(namespace).map { it.hostname }

    override fun exists(domain: String): Boolean = domain in listDomains()

    override fun addDomain(domain: String) {
        validateDomain(domain)
        if (exists(domain)) {
            throw IllegalArgumentException("域名已存在: $domain")
        }
        val ingress = template.render(domain)
        kubernetesClientService.applyIngress(namespace, ingress)
        logger.info { "已按模板添加域名 $domain 到 $namespace" }
    }

    override fun removeDomain(domain: String): Boolean {
        validateDomain(domain)
        val removed = kubernetesClientService.removeIngressWithHost(namespace, domain)
        if (removed) logger.info { "已删除域名 $domain" }
        return removed
    }
}
