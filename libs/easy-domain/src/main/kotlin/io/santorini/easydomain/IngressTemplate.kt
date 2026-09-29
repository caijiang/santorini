package io.santorini.easydomain

import io.fabric8.kubernetes.api.model.networking.v1.Ingress
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder
import io.fabric8.kubernetes.client.utils.Serialization
import io.github.oshai.kotlinlogging.KotlinLogging

@Suppress("unused")
private val logger = KotlinLogging.logger {}

/**
 * Ingress YAML 模板。
 *
 * 模板正文中所有 `{{domain}}` 占位符在 [render] 时替换为目标域名——
 * 包括 `spec.rules[].host`，也可以出现在 `tls.secretName` 等任何位置。
 * 模板在构造时即校验（必须能反序列化为 Ingress），坏模板在启动期暴露而不是首次添加域名时。
 *
 * @throws IllegalArgumentException YAML 无法反序列化为 Ingress
 */
class IngressTemplate(val yaml: String) {

    private val placeholder = "{{domain}}"

    private val templateContainsPlaceholder: Boolean
        get() = yaml.contains(placeholder)

    init {
        require(templateContainsPlaceholder || parse().spec.rules.isNotEmpty()) {
            "ingress 模板不合法：既没有 {{domain}} 占位符，rules 也为空"
        }
        parse() // 校验反序列化
    }

    /**
     * @param domain 目标域名
     * @return 渲染并反序列化后的 Ingress 对象
     */
    fun render(domain: String): Ingress {
        require(templateContainsPlaceholder) {
            "ingress 模板缺少 $placeholder 占位符，无法渲染域名"
        }
        val rendered = yaml.replace(placeholder, domain)
        val ingress = parse(rendered)
        require(ingress.spec.rules.any { it.host == domain }) {
            "渲染后 Ingress 的 rules 中找不到 host=$domain，请检查模板"
        }
        // 资源名由域名决定：k8s 资源名不允许 '.'，统一净化。
        // 这样同一域名重复添加幂等（同名覆盖），不同域名之间也不会因模板里的静态 name 而冲突。
        val ingress1 = IngressBuilder(ingress)
            .editMetadata()
            .withName(sanitizedName(domain))
            .endMetadata()
            .build()

        ingress1.spec?.tls?.firstOrNull()
            ?.secretName = "tls-" + sanitizedName(domain)

        return ingress1
    }

    /**
     * `a.example.com` → `a-example-com`；通配符 `*.example.com` → `wildcard-example-com`
     */
    fun sanitizedName(domain: String): String {
        val base = domain.removePrefix("*.").replace(".", "-").lowercase()
        return if (domain.startsWith("*.")) "wildcard-$base" else base
    }

    private fun parse(yamlText: String = yaml): Ingress =
        Serialization.unmarshal(yamlText, Ingress::class.java)
            ?: throw IllegalArgumentException("ingress 模板无法反序列化为 Ingress")

    override fun toString(): String = "IngressTemplate(${yaml.hashCode()})"
}
