@file:OptIn(ExperimentalSerializationApi::class)

package me.jiangcai.cr

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import me.jiangcai.cr.docker.ContainerImageV1
import me.jiangcai.cr.docker.ManifestV2
import me.jiangcai.cr.docker.ResourceConfig
import me.jiangcai.cr.ico.OciImageIndexV1

private val logger = KotlinLogging.logger {}

/**
 * [参考](https://distribution.github.io/distribution/spec/api/?utm_source=chatgpt.com)
 * @author CJ
 */
@Suppress("UastIncorrectHttpHeaderInspection")
class ContainerRegistry(
    inputClient: HttpClient = HttpClient(Apache),
    private val client: HttpClient = inputClient.config {
        install(ContentNegotiation) {
            json(Json)
        }
        install(HttpRedirect) {
            checkHttpMethod = true
            allowHttpsDowngrade = true
        }
    }
) {

    private val tokens: MutableMap<Pair<String, String?>, String> = mutableMapOf()

    /**
     * [第一步](https://distribution.github.io/distribution/spec/api/?utm_source=chatgpt.com#get-manifest),有时候会再走一下[第二步](https://distribution.github.io/distribution/spec/api/?utm_source=chatgpt.com#get-blob)
     */
    suspend fun queryStatus(image: Image, authProvider: AuthProvider? = null): Pair<String, List<Deployable>>? {
        // registry-vpc.cn-hangzhou.aliyuncs.com/lecai-flow/lecai-mall
        // 401 继续处理
        // 403 再见,429 再见
        // 404 再见
        // 400 算是技术故障
        val ociImageIndexV1 = ContentType.parse("application/vnd.oci.image.index.v1+json")
        val dockerManifestV2 = ContentType.parse("application/vnd.docker.distribution.manifest.v2+json")
        return client.get("https://${image.registryHost}/v2/${image.visitName}/manifests/${image.tag}") {
            fillCommonHeader(image, authProvider)
            accept(ociImageIndexV1)
            accept(dockerManifestV2)
            // 没发现不考虑
//            accept(ContentType.parse("application/vnd.docker.distribution.manifest.list.v2+json"))
//            accept(ContentType.parse("application/vnd.oci.image.manifest.v1+json"))
        }
            .let {
                it.commonWith(client, image, authProvider, {
                    queryStatus(image, authProvider)
                }, {
                    when (status) {
                        HttpStatusCode.OK -> {
                            val digest = headers["Docker-Content-Digest"] ?: error("Docker-Content Digest required")
                            // application/vnd.oci.image.index.v1
                            // OciImageIndexV1
                            if (contentType()?.match(ociImageIndexV1) == true) {
                                digest to body<OciImageIndexV1>().manifests
                            } else if (contentType()?.match(dockerManifestV2) == true) {
                                // 就不读取了
                                val rc = body<ManifestV2>().config ?: throw IllegalStateException("Missing config")
                                if (rc.mediaType != "application/vnd.docker.container.image.v1+json") {
                                    throw IllegalStateException("Missing media type: ${rc.mediaType}")
                                }
                                digest to listOf(getBlobAsDockerImage(image, rc, authProvider))
                            } else throw IllegalStateException("暂不支持:${contentType()}")
                        }

                        HttpStatusCode.NotFound -> {
                            null
                        }

                        else -> throw IllegalStateException("未知的异常:${status}")
                    }
                })
            }
    }

    private suspend fun getBlobAsDockerImage(
        image: Image,
        rc: ResourceConfig,
        authProvider: AuthProvider?
    ): ContainerImageV1 {
        return client.get("https://${image.registryHost}/v2/${image.visitName}/blobs/${rc.digest}") {
            fillCommonHeader(image, authProvider)
//            accept(ContentType.parse("application/vnd.docker.container.image.v1+json"))
        }.let {
            it.commonWith(client, image, authProvider, {
                getBlobAsDockerImage(image, rc, authProvider)
            }, {

                // https://github.com/opsencontainers/image-spec/blob/main/config.md
                Json.decodeFromStream<ContainerImageV1>(
                    this.bodyAsChannel().toInputStream()
                )
//                body<ContainerImageV1>()
            })
        }
    }

    private fun HttpRequestBuilder.fillCommonHeader(
        image: Image,
        authProvider: AuthProvider?
    ) {
        tokens[image.registryHost to authProvider?.ownerName]?.let {
            logger.info { "Found token: $it" }
            this.header("Authorization", it)
        }
    }

    /**
     * [参考](https://distribution.github.io/distribution/spec/api/?utm_source=chatgpt.com#get-tags)
     */
    suspend fun readTags(image: Image, authProvider: AuthProvider? = null): Tags? {
        return client.get("https://${image.registryHost}/v2/${image.visitName}/tags/list") {
            fillCommonHeader(image, authProvider)
        }
            .let {
                it.commonWith(client, image, authProvider, {
                    readTags(image, authProvider)
                }, {
                    when (status) {
                        HttpStatusCode.OK -> {
                            body<Tags>()
                        }

                        HttpStatusCode.NotFound -> {
                            null
                        }

                        else -> throw IllegalStateException("未知的异常:${status}")
                    }
                })
            }
    }

    private suspend fun <T> HttpResponse.commonWith(
        client: HttpClient,
        image: Image,
        authProvider: AuthProvider?,
        retry: suspend () -> T,
        block: suspend HttpResponse.() -> T
    ): T {
        val httpResponse = this
        if (httpResponse.status == HttpStatusCode.BadRequest) {
            logger.error { "Bad request,需要技术参与了" }
            throw IllegalStateException("Bad request")
        }
        if (httpResponse.status == HttpStatusCode.Forbidden) {
            throw IllegalStateException("Forbidden")
        }
        if (httpResponse.status == HttpStatusCode.TooManyRequests) {
            throw IllegalStateException("TooManyRequests")
        }
        if (httpResponse.status == HttpStatusCode.Unauthorized) {
            val x = httpResponse.headers["www-authenticate"] ?: throw IllegalStateException("Unauthorized")
            val header = RFC7235Header.ofString(x)

            header.properties["error"]?.let {
                throw IllegalStateException("授权登录后, Error: $it")
            }

            // https://distribution.github.io/distribution/spec/auth/oauth/#refresh-token-format
            return client.get(header.realm) {
                (header.properties + (authProvider?.parametersForTokenAuth() ?: emptyMap())).forEach { (key, value) ->
                    parameter(key, value)
                }
                authProvider?.headersForTokenAuth()?.forEach { (t, u) ->
                    header(t, u)
                }
            }.let {
                if (it.status != HttpStatusCode.OK) {
                    throw IllegalStateException("Unsuccessful authentication")
                }

                val tks = it.body<TokenResponse>()
                logger.debug { "Received token: $tks" }
                val pair = image.registryHost to authProvider?.ownerName
                tokens[pair] = header.type + " " + tks.readToken
                retry()
            }
        }
        return block()
    }
}