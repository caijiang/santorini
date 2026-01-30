@file:Suppress("TestFunctionName", "NonAsciiCharacters")

package io.santorini.console

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.santorini.console.model.PageResult
import io.santorini.console.schema.ServiceMetaData
import io.santorini.consoleModuleEntry
import io.santorini.io.santorini.test.MockJobService
import io.santorini.model.Lifecycle
import io.santorini.model.ServiceType
import io.santorini.test.mockUserModule
import io.santorini.tools.addServiceMeta
import io.santorini.tools.createStandardClient
import kotlin.test.Test

/**
 * @author CJ
 */
class ServiceKtTest {
    @Test
    fun 服务单元测试() = testApplication {
        application {
            consoleModuleEntry(
                scheduleJobServiceLoader = { _, _ -> MockJobService }
            )
            mockUserModule()
        }

        val c = createStandardClient()

        c.get("/mockUser/Manager").apply {
            status shouldBe HttpStatusCode.OK
        }

        val demoService = ServiceMetaData(
            id = "demo-service", name = "范例", type = ServiceType.JVM, requirements = null,
            lifecycle = Lifecycle(),
        )
        c.addServiceMeta(demoService)
        // 分页获取,
        c.get("https://localhost/services").apply {
            status shouldBe HttpStatusCode.OK
            // 分页结果
            body<List<ServiceMetaData>>().apply {
                println(this)
            }
                .shouldNotBeNull()
                .shouldContain(demoService)
        }
        c.get("https://localhost/services?keyword=${demoService.id}&limit=1").apply {
            status shouldBe HttpStatusCode.OK
            // 分页结果
            body<PageResult<ServiceMetaData>>().apply {
                println(this)
            }
                .shouldNotBeNull()
                .apply {
                    total shouldBeGreaterThan 0
                    records shouldContain demoService
                }
        }

        c.get("https://localhost/services/${demoService.id}").apply {
            status shouldBe HttpStatusCode.OK
            contentType() shouldNotBeNull {
                match(ContentType.Application.Json) shouldBe true
            }
        }

        c.get("https://localhost/services/${demoService.id}/lastRelease/test").apply {
            status shouldBe HttpStatusCode.OK
        }

    }
}