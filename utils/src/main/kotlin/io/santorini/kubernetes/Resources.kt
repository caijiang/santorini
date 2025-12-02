package io.santorini.kubernetes

import io.fabric8.kubernetes.client.KubernetesClient
import io.santorini.model.ResourceType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.Charset
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

    val allNames = configs.map { it.metadata.name }.toMutableSet()
    allNames.addAll(secrets.map { it.metadata.name })

    return allNames.map { name ->
        configs.firstOrNull {
            it.metadata.name == name
        } to secrets.firstOrNull {
            it.metadata.name == name
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
            list.map { it.metadata.name }.first(),
            list.firstNotNullOfOrNull { it.metadata.labels["santorini.io/description"] },
            publicProperties,
            properties,
        )
    }
}