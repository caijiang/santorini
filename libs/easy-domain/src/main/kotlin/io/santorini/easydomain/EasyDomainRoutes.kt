package io.santorini.easydomain

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.kubernetes.KubernetesClientService

private val logger = KotlinLogging.logger {}

/**
 * easy-domain 域名管理的一键装配。
 *
 * - `EASY_DOMAIN_NAMESPACE` + 模板未配齐：什么都不挂，仅 info 提示
 *
 * 这里**只**挂域名管理路由。证书同步与本路由无关（不由"新增域名"触发），
 * 需要用 [certSyncService] 单独装配、由宿主的调度任务驱动。
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
