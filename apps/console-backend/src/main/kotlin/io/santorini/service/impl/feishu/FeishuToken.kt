package io.santorini.service.impl.feishu

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

@Serializable
data class FeishuToken(
    val token: String,
    val expireTimeSeconds: Long,
) {
    /**
     * @return 接下来的时间内可以工作
     */
    fun workInNext(duration: Duration): Boolean {
        return Clock.System.now().plus(duration) < Instant.fromEpochSeconds(expireTimeSeconds)
    }
}