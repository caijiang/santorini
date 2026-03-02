package io.santorini.informer

import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfigBuilder
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElector
import io.fabric8.kubernetes.client.informers.ResourceEventHandler
import io.fabric8.kubernetes.client.informers.SharedIndexInformer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.console.schema.HpaStatusService
import io.santorini.informer.k8s.autoScalingHappen
import io.santorini.informer.k8s.serviceInstanceUnstable
import io.santorini.kubernetes.belongs
import io.santorini.kubernetes.toCurrentValue
import io.santorini.kubernetes.toHpaTarget
import io.santorini.service.AsyncTaskService
import io.santorini.service.NoticeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.time.Clock

/**
 * @author CJ
 */
class KubernetesInformerServiceImpl(
    private val kubernetesClient: KubernetesClient,
    private val noticeService: NoticeService,
    private val asyncTaskService: AsyncTaskService,
    private val hpaStatusService: HpaStatusService,
) : KubernetesInformerService {
    private var allInformers: List<SharedIndexInformer<*>>? = null
    private val logger = KotlinLogging.logger {}

    override fun start() {
        logger.info { "start informer" }
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
            kubernetesClient.autoScalingHappen(it, handler = object : ResourceEventHandler<HorizontalPodAutoscaler> {
                override fun onAdd(obj: HorizontalPodAutoscaler?) {
                }

                override fun onUpdate(oldObj: HorizontalPodAutoscaler?, newObj: HorizontalPodAutoscaler?) {
                    // 移除  1小时前的
                    // 移除 resource_metric_source_name 不再范围的
                    // 移除 resource_metric_source_name 但 type 不匹配的
                    val now = Clock.System.now()
                    val hpa = newObj?.toHpaTarget() ?: return
                    val current = newObj.toCurrentValue(hpa) ?: return
                    val (env, serviceId) = newObj.belongs()
                    val si = serviceId ?: return
                    asyncTaskService.submit {
                        hpaStatusService.insertTimeline(
                            now, env, si, hpa, current
                        )
                    }
                }

                override fun onDelete(obj: HorizontalPodAutoscaler?, deletedFinalStateUnknown: Boolean) {
                    val hpa = obj ?: return
                    asyncTaskService.submit {
                        val (env, serviceId) = hpa.belongs()
                        serviceId?.let { service ->
                            hpaStatusService.deleteBy(env, service)
                        }
                    }
                }

            }) { oldDesiredReplicas, newDesiredReplicas, oldObj, newObj ->
                noticeService.autoScalingHappen(oldDesiredReplicas, newDesiredReplicas, oldObj, newObj)
            }
        }
    }

    override fun stop() {
        logger.info { "stop informer" }
        allInformers?.forEach { informer ->
            informer.close()
        }
        allInformers = null
    }

    private var identity: String? = null
    private var callback: LeaderCallbacks? = null
    override fun initLocker(identity: String, callback: LeaderCallbacks) {
        this.identity = identity
        this.callback = callback
    }

    override suspend fun loopForLocker() {
        val identity = this.identity ?: return
        val callback = this.callback ?: return

        logger.info { "Kubernetes informer loop for $identity" }
        val name = identity.substring(0, identity.lastIndexOf('-'))
        val leaseName = "$name-inform"
        // 创建 LeaseLock
        val lock = withContext(Dispatchers.IO) {
            io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock(
                kubernetesClient.namespace,
                leaseName,
                identity
            )
        }

        // 配置 Leader Election
        val config = LeaderElectionConfigBuilder()
            .withName("InformLock Election")
            .withLeaseDuration(Duration.ofSeconds(15))
            .withRenewDeadline(Duration.ofSeconds(3))
            .withRetryPeriod(Duration.ofSeconds(2))
            .withLeaderCallbacks(callback)
            .withLock(lock)
            .build()

        // 创建 Leader Elector
        val elector = LeaderElector(
            kubernetesClient, config, Executors.newSingleThreadExecutor {
                val t = Thread(it)
                t.name = "Informer"
                t.isDaemon = true
                t
            }
        )

        // 启动 Leader Election（阻塞运行）
        elector.start()
        logger.info { "elector started." }
    }
}