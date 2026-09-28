package io.santorini.service.impl.feishu

import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.kubernetes.KubernetesClientService

/**
 * 飞书 token 的持久化：以 Secret 形式保存在当前命名空间。
 *
 * 与 k8s 客户端本身的通用能力分开——k8s-client 模块不该认识"飞书"这个业务概念。
 * @author CJ
 */
interface FeishuTokenStore {
    fun query(id: String): FeishuToken?

    fun save(id: String, token: FeishuToken)
}

class KubernetesFeishuTokenStore(
    private val kubernetesClientService: KubernetesClientService
) : FeishuTokenStore {
    private val logger = KotlinLogging.logger {}

    private fun secretName(id: String): String {
        return "feishu-access-token-${id.replace("[^a-zA-Z0-9]".toRegex(), "")}"
    }

    override fun query(id: String): FeishuToken? {
        val data = kubernetesClientService.readStringSecret(kubernetesClientService.namespace, secretName(id))
            ?: return null

        val token = data["token"]
        val expiration = data["expiration"]?.toLongOrNull()
        if (token == null || expiration == null) {
            logger.warn {
                "飞书 token 的 secret 内容非法,缺少 token 或 expiration:${data.keys}"
            }
            return null
        }
        return FeishuToken(token, expiration)
    }

    override fun save(id: String, token: FeishuToken) {
        kubernetesClientService.applyStringSecret(
            kubernetesClientService.namespace,
            secretName(id),
            mapOf(
                "token" to token.token,
                "expiration" to token.expireTimeSeconds.toString()
            )
        )
    }
}
