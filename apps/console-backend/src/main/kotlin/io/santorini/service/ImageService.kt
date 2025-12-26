package io.santorini.service

import io.fabric8.kubernetes.client.KubernetesClient
import io.ktor.client.*
import io.santorini.kubernetes.findDockerConfigJson
import io.santorini.schema.DeploymentDeployData
import kotlinx.serialization.json.Json
import me.jiangcai.cr.ContainerRegistry
import me.jiangcai.cr.Deployable
import me.jiangcai.cr.DockerAuthsConfig
import me.jiangcai.cr.Image

/**
 * @author CJ
 */
class ImageService(
    private val kubernetesClient: KubernetesClient,
    private val httpClient: HttpClient,
) {

    suspend fun toImageInfo(envId: String, data: DeploymentDeployData): Pair<String, List<Deployable>>? {
        val image = Image.ofStringAndTag(data.imageRepository, data.imageTag ?: "latest")
//        data.pullSecretName
        // 获取密钥
        val auth = data.pullSecretName?.mapNotNull {
            kubernetesClient.findDockerConfigJson(envId, it)
        }?.firstNotNullOfOrNull {
            Json.decodeFromString<DockerAuthsConfig>(it).auths[image.registryHost]
        }
        val registry = ContainerRegistry(httpClient)

        return registry.queryStatus(image, auth?.toAuthProvider("owner"))
    }
}