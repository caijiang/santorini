package io.santorini.service.impl.feishu

import io.santorini.console.schema.NoticeTargetUser

/**
 * @author CJ
 */
data class FeishuUser(
    val feishuOpenId: String,
    val user: NoticeTargetUser
)
