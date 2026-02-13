package io.santorini.informer

import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

interface KubernetesInformerService {

    fun start()
    fun stop()

    /**
     * 如果可以获得租约，则尝试开启 informer 工作;如果失去就停止
     */
    fun loopForLocker(
        identity: String = System.getenv("HOSTNAME"), callback: LeaderCallbacks = LeaderCallbacks(
            {
                logger.info {
                    "I got the lock"
                }
                start()
                // 不可以在此阻塞
            },
            {
                logger.info {
                    "I lost the lock"
                }
                stop()
                // 不可以在此阻塞
            },  // 停止 Leader 时的回调
            { _: String? -> } // Leader 变更时的回调
        )
    )

}
