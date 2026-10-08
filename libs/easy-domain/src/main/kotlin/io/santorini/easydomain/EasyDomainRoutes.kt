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
 *
 * ## 响应体只允许是 JSON（或空）
 *
 * `call.respond(someString)` 不会走 ContentNegotiation 的 kotlinx 转换器 —— Ktor 内置的
 * `DefaultTextContentConverter` 会先接走，发出 `Content-Type: text/plain`。
 * 而前端的 `fetchBaseQuery` 默认 `responseHandler: 'json'`，对非空 body 一律 `JSON.parse`，
 * 于是 `text/plain` 的裸字符串会变成 `PARSING_ERROR`；又因为共享的 `apiBase` 用
 * `retry(..., maxRetries = 1)` 且默认重试条件不看错误类型，**同一个 POST 会被重发一次**。
 * 要返回标量就包一层对象（见 `GET /domains/{domain}`），否则干脆不带 body。
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
                if (domainService.exists(domain)) call.respond(mapOf("domain" to domain))
                else call.respond(HttpStatusCode.NotFound)
            }
            post("/{domain}") {
                val domain = call.parameters["domain"]!!
                try {
                    domainService.addDomain(domain)
                    // 不返回 body：本接口是 JSON API，而 `respond(domain)` 里的裸 String
                    // 会被 Ktor 的 DefaultTextContentConverter 接走、发成 text/plain，
                    // 前端的 JSON 解析会直接抛 SyntaxError（见本文件顶部说明）
                    call.respond(HttpStatusCode.Created)
                } catch (e: IllegalArgumentException) {
                    // 出错原因只进日志：本仓的约定是 4xx 不带 body（见 console-backend Env.kt），
                    // 带 body 反而会让共享 http 层多弹一个空提示
                    logger.warn { "拒绝新增域名 $domain：${e.message}" }
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
            delete("/{domain}") {
                val domain = call.parameters["domain"]!!
                try {
                    if (domainService.removeDomain(domain)) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound)
                } catch (e: IllegalArgumentException) {
                    logger.warn { "拒绝删除域名 $domain：${e.message}" }
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
        }
    }
}
