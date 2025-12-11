package io.santorini.console

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.santorini.consoleModule
import io.santorini.model.PageResult
import io.santorini.model.ServiceType
import io.santorini.schema.ServiceMetaData
import io.santorini.schema.mergeJson
import io.santorini.test.mockUserModule
import io.santorini.tools.createStandardClient
import kotlin.test.Test

/**
 * @author CJ
 */
class ServiceKtTest {
    @Test
    fun testRoot() = testApplication {
        application {
            consoleModule()
            mockUserModule()
        }

        val c = createStandardClient()

        c.get("/mockUser/Manager").apply {
            status shouldBe HttpStatusCode.OK
        }

        val demoService = ServiceMetaData(
            id = "demo-service", name = "范例", type = ServiceType.JVM, requirements = null
        )
        c.post("https://localhost/services") {
            contentType(ContentType.Application.Json)
            setBody(
                mergeJson(
                    demoService, """
  {
    "resources":{
     "cpu": {
      "requestMillis":100,
      "limitMillis":1000
     }
    }
  }
"""
                )
            )
//            setBody(EnvData(id = "id", name = "test", production = true))
//            setBody("")
        }.apply {
            status shouldBe HttpStatusCode.OK
        }
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