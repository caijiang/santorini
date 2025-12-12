package io.santorini.tools

import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.santorini.schema.ServiceMetaData
import io.santorini.schema.mergeJson


suspend fun HttpClient.addServiceMeta(data: ServiceMetaData) {
    post("https://localhost/services") {
        contentType(ContentType.Application.Json)
        setBody(
            mergeJson(
                data, """
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
}
   