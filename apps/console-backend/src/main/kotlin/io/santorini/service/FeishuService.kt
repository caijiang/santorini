package io.santorini.service

import io.santorini.service.impl.feishu.FeishuPost

/**
 * @author CJ
 */
interface FeishuService {
    /**
     * https://open.feishu.cn/document/server-docs/im-v1/message/create
     */
    suspend fun sendSingleMessage(openId: String, post: FeishuPost)
}