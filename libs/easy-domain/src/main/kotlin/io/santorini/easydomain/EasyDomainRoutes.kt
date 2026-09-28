package io.santorini.easydomain

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.kubernetes.KubernetesClientService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * easy-domain 一键装配。
 *
 * - `EASY_DOMAIN_NAMESPACE` + 模板未配齐：什么都不挂，仅 info 提示
 * - 阿里云配置未配齐：域名管理可用，证书同步不启用
 *
 * 新增域名成功后自动触发一次证书同步（后台执行，失败只记日志）。
 */
fun Application.easyDomain(
    config: EasyDomainConfig,
    kubernetesClientService: KubernetesClientService,
) {
    if (!config.domainFeatureEnabled) {
        logger.info { "easy-domain 未启用：需要同时设置 EASY_DOMAIN_NAMESPACE 与 ingress 模板" }
        return
    }
    logger.info { "easy-domain 启用，namespace=${config.namespace}" }

    val template = IngressTemplate(config.ingressTemplate!!)
    val domainService = DomainServiceImpl(kubernetesClientService, config.namespace!!, template)

    val certSyncService = if (config.certSyncEnabled) {
        logger.info { "easy-domain 证书同步启用，endpoint=${config.aliyunEndpoint}" }
        CertSyncServiceImpl(
            kubernetesClientService,
            config.namespace,
            DnsScopeMatcher(config.dnsCidrs, config.dnsCnameSuffixes),
            CasCertificateUploader(
                config.aliyunAccessKeyId!!,
                config.aliyunAccessKeySecret!!,
                config.aliyunEndpoint!!,
            ),
        )
    } else {
        logger.info { "easy-domain 证书同步未启用：需要阿里云 RAM 账号与 endpoint" }
        null
    }

    val certSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 宿主可能已装 ContentNegotiation；插件重复安装会抛异常，这里守一下
    if (pluginOrNull(ContentNegotiation) == null) {
        install(ContentNegotiation) {
            json()
        }
    }

    routing {
        route("/domains") {
            get {
                call.respond(domainService.listDomains())
            }
            get("/{domain}") {
                val domain = call.parameters["domain"]!!
                if (domainService.exists(domain)) call.respond(domain)
                else call.respond(HttpStatusCode.NotFound)
            }
            post("/{domain}") {
                val domain = call.parameters["domain"]!!
                try {
                    domainService.addDomain(domain)
                    call.respond(HttpStatusCode.Created, domain)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, e.message ?: "invalid domain")
                }
                certSyncService?.let { service ->
                    certSyncScope.launch {
                        runCatching { service.syncEligibleCerts() }
                            .onFailure { logger.warn(it) { "域名 $domain 新增后的证书同步失败" } }
                    }
                }
            }
            delete("/{domain}") {
                val domain = call.parameters["domain"]!!
                try {
                    if (domainService.removeDomain(domain)) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, e.message ?: "invalid domain")
                }
            }
        }
    }
}
