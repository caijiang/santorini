package io.santorini.service.impl

import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerBuilder
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricSpecBuilder
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricStatusBuilder
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceBuilder
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.santorini.InSiteUserData
import io.santorini.console.schema.*
import io.santorini.model.ServiceType
import io.santorini.service.AsyncTaskServiceImpl
import io.santorini.service.KubernetesClientService
import io.santorini.service.NoticeService
import io.santorini.service.SiteService
import io.santorini.service.impl.feishu.FeishuServiceImpl
import io.santorini.service.impl.feishu.workWithLocalFeishu
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

/**
 * @author CJ
 */
class NoticeServiceImplTest {
    private val testNamespace = "test"
    private val testServiceId = "demo-service"
    private val demoUserId = Uuid.random()

    private fun withNoticeService(block: suspend NoticeService.() -> Unit) {
        runTest {
            workWithLocalFeishu(javaClass) {
                val config = this
                val notice = NoticeServiceImpl(
                    AsyncTaskServiceImpl(),
//                    object : AsyncTaskService {
//                        override fun submit(task: suspend () -> Unit) {
//                            task.invoke()
//                        }
//
//                        override fun close() {
//                        }
//
//                    },
                    mockk<UserCareServiceMetaService>().apply {
                        val svc = this
                        coEvery {
                            svc.listNoticeTarget(
                                matchNullable {
                                    it == null || it == testNamespace
                                }, eq(testServiceId)
                            )
                        } returns listOf(
                            NoticeTargetUser(demoUserId, "abc", config.demoUserOpenId)
                        )
                    },
                    mockk<ServiceMetaService>().apply {
                        val serviceMeta = this
                        coEvery {
                            serviceMeta.readServiceMetaData(eq(testServiceId))
                        } returns ServiceMetaData(
                            id = testServiceId,
                            name = testServiceId + "服务",
                            type = ServiceType.JVM,
                            requirements = listOf(),
                            lifecycle = null,
                        )
                    },
                    mockk<EnvService>().apply {
                        val envService = this
                        coEvery {
                            envService.read(eq(listOf(testNamespace)))
                        } returns listOf()
                        coEvery {
                            envService.read(any())
                        } returns listOf()
                    },
                    FeishuServiceImpl(
                        mockk<KubernetesClientService>().apply {
                            val service = this
                            coEvery {
                                service.queryFeishuToken(eq(config.id))
                            } returns null
                            coEvery {
                                service.saveFeishuToken(any(), any())
                            } returns Unit
                        },
                        HttpClient(Apache) {
                            install(ContentNegotiation) {
                                json(Json)
                            }
                        },
                        config.id, config.secret
                    ),
                    mockk<SiteService>().apply {
                        val siteService = this
                        every {
                            siteService.appName
                        } returns "应用啊"
                        every {
                            siteService.siteHome
                        } returns "https://santorini-site.app.com"
                    },
                )
                block(notice)
            }

        }
    }

    @Test
    fun serviceInstanceUnstable() {
        withNoticeService {
            serviceInstanceUnstable(
                "111.111.111.11",
                EndpointSliceBuilder()
                    .withNewMetadata()
                    .withNamespace(mockMetadataNamespace())
                    .withLabels<String, String>(mockMetadataLabels())
                    .endMetadata()
                    .build(),
                EndpointSliceBuilder().build(),
            )
        }
        takeRest()
    }

    private fun mockMetadataLabels(): Map<String, String> = mapOf("kubernetes.io/service-name" to testServiceId)

    private fun mockMetadataNamespace(): String = testNamespace

    @Test
    fun autoScalingHappen() {
        withNoticeService {
            val obj = HorizontalPodAutoscalerBuilder()
                .withNewMetadata()
                .withNamespace(mockMetadataNamespace())
                .withLabels<String, String>(mockMetadataLabels())
                .endMetadata()
                .withNewSpec()
                .withNewScaleTargetRef()
                .withName(testServiceId)
                .endScaleTargetRef()
                // 这是 cpu
                .withMetrics(
                    MetricSpecBuilder()
                        .withType("Resource")
                        .withNewResource()
                        .withName("cpu")
                        .withNewTarget()
                        .withType("Utilization")
                        .withAverageUtilization(50)
                        .endTarget()
                        .endResource()
                        .build()
                )
                .endSpec()
                .withNewStatus()
                .withCurrentMetrics(
                    MetricStatusBuilder()
                        .withType("Resource")
                        .withNewResource()
                        .withNewCurrent()
                        .withAverageUtilization(80)
                        .endCurrent()
                        .endResource()
                        .build()
                )
                .endStatus()
                .build()
            autoScalingHappen(
                1, 5,
                obj,
                obj,
            )
        }
        takeRest()
    }

    @Test
    fun newDeployment() {
        val id = Uuid.random()
        withNoticeService {
            this.newDeployment(
                mockk<InSiteUserData>(relaxed = true).apply {
                    val d = this
                    every {
                        d.name
                    } returns "某个用户"
                    every {
                        d.id
                    } returns id
                },
                mockk<DeploymentDeployData>(relaxed = true).apply {
                    val d = this
                    every {
                        d.imageUrl
                    } returns "target"
                },
                DeploymentResource.Deploy(
                    DeploymentResource(), testServiceId, testNamespace
                ),
            )
        }
        takeRest()
    }

    @Test
    fun serviceMetaDataUpdated() {
        val id = Uuid.random()
        withNoticeService {
            this.serviceMetaDataUpdated(
                mockk<InSiteUserData>(relaxed = true).apply {
                    val d = this
                    every {
                        d.name
                    } returns "某个用户"
                    every {
                        d.id
                    } returns id
                },
                testServiceId,
            )
        }
        takeRest()
    }

    private fun takeRest() {
//        logger.info { "sleep!" }
        Thread.sleep(500)
    }
}