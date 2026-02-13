package io.santorini.informer

import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks
import io.mockk.mockk
import io.santorini.informer.k8s.autoScalingHappen
import io.santorini.informer.k8s.serviceInstanceUnstable
import io.santorini.io.santorini.service.workWithLocalKubernetesCluster
import org.junit.jupiter.api.Disabled
import kotlin.test.Test


/**
 * @author CJ
 */
@Disabled("预览而已")
class KubernetesInformerServiceImplTest {

    @Test
    fun previewWatch() {
        workWithLocalKubernetesCluster(javaClass) {
            val service = KubernetesInformerServiceImpl(this, mockk())
            val namespace = "test-ns"
            // 不再继续研究，计划用应用获取到的操作作为
//            val deploymentInformer = this.apps().deployments()
//                .inNamespace(
//                    namespace
//                )
//                .inform()
//            deploymentInformer.addEventHandler(
//                object : ResourceEventHandler<Deployment> {
//                    override fun onAdd(obj: Deployment?) {
////                        println(obj)
//                    }
//
//                    override fun onUpdate(oldObj: Deployment?, newObj: Deployment?) {
////                        println("onUpdate----->\noldObj: $oldObj\r\nnewObj: $newObj\r\neq:${oldObj == newObj}")
//                        if (oldObj?.equalsGeneration(newObj) != true) {
//                            println("onGenerationUpdate----->\noldObj: $oldObj\r\nnewObj: $newObj\r\neq:${oldObj == newObj}")
//                        }
//                    }
//
//                    override fun onDelete(obj: Deployment?, deletedFinalStateUnknown: Boolean) {
//                    }
//                }
//            )

            // curl -X POST http://localhost:8080/actuator/restart
            this.serviceInstanceUnstable(namespace) { address, oldObj, newObj ->
                println("serviceInstanceUnstable:$address----->\noldObj: $oldObj\r\nnewObj: $newObj")
            }

            this.autoScalingHappen(namespace) { oldDR, newDR, oldObj, newObj ->
                println("onHorizontalPodAutoscalerUpdate----->\noldObj: $oldObj\r\nnewObj: $newObj")
                println("从$oldDR 变更到$newDR")
            }

            service.loopForLocker(
                "release-name-santorini-santorini-console-backend-66dd8fdb9n7glz",
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
                )
            )

            println("working...")

            Thread.sleep(600000)

        }
    }
}
