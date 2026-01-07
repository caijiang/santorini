package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.Resource
import io.santorini.model.ResourceType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.Charset
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.*

/**
 * santorini 资源是直接隶属于 k8s的
 */
interface SantoriniResource {
    val type: ResourceType
    val name: String
    val description: String?
    val publicProperties: Map<String, String>

    // 作为列表以上的足够了
    fun <T> readResource(serializer: KSerializer<T>): T
    fun readAllProperties(): Map<String, String>
}

class SantoriniResourceKubernetesImpl(
    override val type: ResourceType,
    override val name: String,
    override val description: String?,
    override val publicProperties: Map<String, String>,
    private val properties: Map<String, String>,
) : SantoriniResource {
    override fun <T> readResource(serializer: KSerializer<T>): T {
        return Json.decodeFromString(serializer, Json.encodeToString(properties))
    }

    override fun readAllProperties(): Map<String, String> = properties
}

/**
 * 在`0826803e510ecb7d5993e1067c94d42fdbec5a96`之前是直接使用 name 作为资源名；这个非常糟糕
 */
private fun ObjectMeta.toResourceName(): String {
    val id = labels["santorini.io/id"]
    if (id?.isNotBlank() == true) {
        return id
    }
    return name
}

/**
 *
 */
fun KubernetesClient.findResourcesInNamespace(namespace: String, type: ResourceType? = null): List<SantoriniResource> {
    val configs = configMaps().inNamespace(namespace)
        .let {
            type?.let { t ->
                it.withLabel("santorini.io/resource-type", t.name)
            } ?: it.withLabel("santorini.io/resource-type")
        }.list().items

    val secrets = secrets().inNamespace(namespace)
        .let {
            type?.let { t ->
                it.withLabel("santorini.io/resource-type", t.name)
            } ?: it.withLabel("santorini.io/resource-type")
        }.list().items

    val allNames = configs.map { it.metadata.toResourceName() }.toMutableSet()
    allNames.addAll(secrets.map { it.metadata.toResourceName() })

    return allNames.map { name ->
        configs.firstOrNull {
            it.metadata.toResourceName() == name
        } to secrets.firstOrNull {
            it.metadata.toResourceName() == name
        }
    }.map { (config, secret) ->
        val list = listOfNotNull(config, secret)
        val publicProperties = config?.data ?: mapOf()
        val properties = publicProperties.toMutableMap()
        secret?.data?.forEach { (t, u) ->
            val bin = Base64.getDecoder().decode(u)
            try {
                properties[t] = bin.toString(Charset.forName("UTF-8"))
            } catch (ignored: Exception) {
            }
        }
        SantoriniResourceKubernetesImpl(
            ResourceType.valueOf(list.firstNotNullOf { it.metadata.labels["santorini.io/resource-type"] }),
            list.map { it.metadata.toResourceName() }.first(),
            list.firstNotNullOfOrNull { it.metadata.labels["santorini.io/description"] },
            publicProperties,
            properties,
        )
    }
}

/**
 * 移除该资源
 */
fun KubernetesClient.removeResource(namespace: String, name: String) {
    // 优先寻找 id 适配的
    removeResourceFrom(configMaps(), namespace, name)
    removeResourceFrom(secrets(), namespace, name)
}

private val nameVersionChangeTime = LocalDateTime.of(2026, 1, 7, 0, 0, 0)
    .toInstant(ZoneOffset.UTC)

fun <T> removeResourceFrom(
    mixedOperation: MixedOperation<T, out KubernetesResourceList<T>, Resource<T>>,
    namespace: String,
    name: String
) where T : HasMetadata, T : Namespaced {
    val s1 = mixedOperation.inNamespace(namespace)
        .withLabel("santorini.io/resource-type")
        .withLabel("santorini.io/id", name)
        .delete()
    if (s1.isNotEmpty())
        return
    // 找不到
    val rs = mixedOperation.inNamespace(namespace)
        .withName(name)
        .get()

    if (rs != null) {
        try {
            if (Instant.parse(rs.metadata.creationTimestamp).isBefore(nameVersionChangeTime)) {
                mixedOperation.inNamespace(namespace)
                    .withName(name).delete()
            }
            return
        } catch (ignored: DateTimeParseException) {
            mixedOperation.inNamespace(namespace)
                .withName(name).delete()
        } catch (ignored: NullPointerException) {
            mixedOperation.inNamespace(namespace)
                .withName(name).delete()
        }
    }
}

/**
 * 构建明文资源
 */
fun KubernetesClient.createEnvResourceInPlain(
    namespace: String,
    data: Map<String, String>,
    labels: Map<String, String> = mapOf("santorini.io/manageable" to "true")
) {
    val item =
        ConfigMapBuilder()
            .withNewMetadata()
            .withNamespace(namespace)
            .withGenerateName("santorini-resource-")
            .withLabels<String, String>(labels)
            .endMetadata()
            .withImmutable(false)
            .withData<String, String>(data)
            .build()

    resource(item).create()
}


fun KubernetesClient.createEnvResourceInSecret(
    namespace: String,
    data: Map<String, String>,
    labels: Map<String, String> = mapOf("santorini.io/manageable" to "true")
) {
    // 更新尝试是失败的，所以我们来干删除再新增
    val item =
        SecretBuilder()
            .withNewMetadata()
            .withNamespace(namespace)
            .withGenerateName("santorini-resource-")
            .withLabels<String, String>(labels)
            .endMetadata()
            .withType("Opaque")
            .withImmutable(false)
//            .withStringData<String, String>(data)
            .withData<String, String>(data.mapValues { (_, v) -> Base64.getEncoder().encodeToString(v.toByteArray()) })
            .build()

    resource(item).create()
}