package io.santorini.informer

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfigBuilder
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElector
import io.fabric8.kubernetes.client.informers.SharedIndexInformer
import io.santorini.informer.k8s.autoScalingHappen
import io.santorini.informer.k8s.serviceInstanceUnstable
import io.santorini.service.NoticeService
import java.time.Duration
import java.util.concurrent.Executors

/**
 * @author CJ
 */
class KubernetesInformerServiceImpl(
    private val kubernetesClient: KubernetesClient, private val noticeService: NoticeService,
) : KubernetesInformerService {
    private var allInformers: List<SharedIndexInformer<*>>? = null

    override fun start() {
        val namespaces = kubernetesClient.namespaces()
            .withLabels(mapOf("santorini.io/manageable" to "true"))
            .list()
            .items
            .map { it.metadata.name }

        // 如果存在
        allInformers?.forEach { informer ->
            informer.close()
        }
        allInformers = null

        allInformers = namespaces.map {
            kubernetesClient.serviceInstanceUnstable(it) { address, old, new ->
                noticeService.serviceInstanceUnstable(address, old, new)
            }
        } + namespaces.map {
            kubernetesClient.autoScalingHappen(it) { oldDesiredReplicas, newDesiredReplicas, oldObj, newObj ->
                noticeService.autoScalingHappen(oldDesiredReplicas, newDesiredReplicas, oldObj, newObj)
            }
        }
    }

    override fun stop() {
        allInformers?.forEach { informer ->
            informer.close()
        }
        allInformers = null
    }

    override fun loopForLocker(identity: String, callback: LeaderCallbacks) {
        val name = identity.substring(0, identity.lastIndexOf('-'))
        val leaseName = "$name-inform"
        // 创建 LeaseLock
        val lock = io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock(
            kubernetesClient.namespace,
            leaseName,
            identity
        )

        // 配置 Leader Election
        val config = LeaderElectionConfigBuilder()
            .withName("InformLock Election")
            .withLeaseDuration(Duration.ofSeconds(15))
            .withRenewDeadline(Duration.ofSeconds(3))
            .withRetryPeriod(Duration.ofSeconds(2))
            .withLeaderCallbacks(
                LeaderCallbacks(
                    {
                        println("${Thread.currentThread().name} I got the lock, and start working on this thread")
                        // 不可以在此阻塞
                    },  // 成为 Leader 时的回调
                    {
                        println("${Thread.currentThread().name} I lost the lock")
                        // 不可以在此阻塞
                    },  // 停止 Leader 时的回调
                    { _: String? -> } // Leader 变更时的回调
                ))
            .withLock(lock)
            .build()

        // 创建 Leader Elector
        val elector = LeaderElector(
            kubernetesClient, config, Executors.newSingleThreadExecutor {
                val t = Thread(it)
                t.name = "InformLock"
                t.isDaemon = true
                t
            }
        )

        // 启动 Leader Election（阻塞运行）
        elector.start()
    }
}