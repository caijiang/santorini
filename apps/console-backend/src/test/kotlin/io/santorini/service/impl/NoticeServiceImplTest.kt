package io.santorini.service.impl

import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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

/**
 * @author CJ
 */
class NoticeServiceImplTest {
    private val logger = KotlinLogging.logger {}

    private val testNamespace = "test"
    private val testServiceId = "demo-service"

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
                                eq(testNamespace), eq(testServiceId)
                            )
                        } returns listOf(
                            NoticeTargetUser("abc", config.demoUserOpenId)
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
        logger.info { "sleep!" }
        Thread.sleep(5000)
    }

    private fun mockMetadataLabels(): Map<String, String> = mapOf("kubernetes.io/service-name" to testServiceId)

    private fun mockMetadataNamespace(): String = testNamespace

    @Test
    fun autoScalingHappen() {

    }

    @Test
    fun newDeployment() {
    }
}