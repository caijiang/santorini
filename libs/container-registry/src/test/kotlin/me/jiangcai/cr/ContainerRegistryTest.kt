@file:Suppress("NonAsciiCharacters", "TestFunctionName")

package me.jiangcai.cr

import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.logging.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * @author CJ
 */
class ContainerRegistryTest {
    private val registry = ContainerRegistry(
        HttpClient(Apache) {
            install(Logging) {
                level = LogLevel.ALL
            }
        }
    )

    @Test
    fun 多架构仓库() = runTest {
        val json = javaClass.getResource("/local-build-mine.json")
        val auth = json?.let { url ->
            Json.decodeFromString<Map<String, String>>(url.readText()).let {
                UsernameAuthProvider(username = it["username"]!!, password = it["password"]!!)
            }
        }
        val f1 = registry.queryStatus(Image.ofString("mysql:latest"), auth)
        assertQueryResult(f1)
        val tags = registry.readTags(Image.ofString("mysql:latest"), auth)
        println(tags)
        tags.shouldNotBeNull()
    }

    private fun assertQueryResult(result: Pair<String, List<Deployable>>?) {
        result.shouldNotBeNull()
        result.second.shouldHaveAtLeastSize(1)
        result.second.forEach {
            println(it.architecture)
        }
    }

    @Test
    fun 匿名仓库1() = runTest {
        val f1 = registry.queryStatus(Image.ofString("ghcr.io/caijiang/santorini-console-frontend"))
        f1?.shouldNotBeNull()
        assertQueryResult(f1)
        val tags = registry.readTags(Image.ofString("ghcr.io/caijiang/santorini-console-frontend"))
        tags.shouldNotBeNull()
    }

    @Test
    fun 授权访问仓库() = runTest {
        val json = javaClass.getResource("/local-build-docker-auth.json")
        if (json != null) {
            val configs = Json.decodeFromString<DockerAuthsConfig>(json.readText())
            val auth = configs.auths["registry.cn-hangzhou.aliyuncs.com"]
                ?: throw Exception("registry.cn-hangzhou.aliyuncs.com missing")

            val image =
                Image.ofString("registry.cn-hangzhou.aliyuncs.com/lecai-flow/ph-spring-boot-demo-base-8:2025-03-19-15-25-59")
            val mf =
                registry.queryStatus(
                    image,
                    auth.toAuthProvider("demo")
                )
            assertQueryResult(mf)

            registry.readTags(image, auth.toAuthProvider("demo")).shouldNotBeNull()
        }

    }


}