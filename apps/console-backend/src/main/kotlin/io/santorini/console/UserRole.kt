package io.santorini.console

import io.fabric8.kubernetes.api.model.rbac.SubjectBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.kubernetes.assignClusterRole
import io.santorini.kubernetes.clusterRoleBindingBySubject
import io.santorini.schema.EnvService
import io.santorini.schema.UserResource
import io.santorini.schema.UserRoleService
import io.santorini.withAuthorization
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

//每当用户登陆后，就会被记录下来
//每当新增一个服务单元，那么就会增加
// ClusterRole*1: 表示所有 namespace 都有的权限
// Role*0: 每次要分配给某一个环境的时候 才生成

// 环境 也可以设置是否公开
// 普通用户 应当收回 list all namespaces 权限;

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalUuidApi::class)
internal fun Application.configureConsoleUser(database: Database, kubernetesClient: KubernetesClient) {
    val service = UserRoleService(database)
    val envService = EnvService(database, kubernetesClient)

    suspend fun toUserEnvs(userId: Uuid, envs: List<Pair<String, String>>): List<String> {
        val user = service.userById(userId.toJavaUuid())
        return if (user == null) {
            listOf()
        } else if (user.grantAuthorities.root) {
            // 如果是超管,则拒绝被访问
            envs.map { it.first }
        } else {
            val allRoles = kubernetesClient.clusterRoleBindingBySubject(
                SubjectBuilder().withKind("ServiceAccount").withNamespace(kubernetesClient.namespace)
                    .withName(user.serviceAccountName).build()
            )
            envs.filter { allRoles.contains(it.second) }.map { it.first }
        }
    }
    routing {
        get<UserResource> {
            withAuthorization(
                {
                    it.grantAuthorities?.users == true
                }) {
                val pr = it.toPageRequest()
                if (pr != null) {
                    call.respond(service.readUserAsPage(it, pr))
                } else {
                    call.respond(service.readUser(it))
                }
            }
        }
        post<UserResource.Id.Envs> {
            val envs = envService.readIdAndVisitableRoleName()
            val targetEnvId = call.receive<String>()
            logger.info { "分配环境${targetEnvId}给${it.id.id}" }
            withAuthorization(
                {
                    val managerRoles = toUserEnvs(
                        it.id, envs
                    )
                    it.grantAuthorities?.users == true && it.grantAuthorities.envs && managerRoles.contains(
                        targetEnvId
                    )
                }) {
                val current = toUserEnvs(it.id.id!!, envs)
                if (current.contains(targetEnvId)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    val roleName = envs.firstOrNull { it.first == targetEnvId }?.second
                    if (roleName != null) {
                        val user = service.userById(it.id.id.toJavaUuid())
                        kubernetesClient.assignClusterRole(
                            roleName,
                            SubjectBuilder().withKind("ServiceAccount").withNamespace(kubernetesClient.namespace)
                                .withName(user?.serviceAccountName).build()
                        )
                        call.respond(HttpStatusCode.Created)
                    } else {
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
        get<UserResource.Id.Envs> {
            withAuthorization(
                {
                    it.grantAuthorities?.users == true && it.grantAuthorities.envs
                }) {
                // 环境的 搞一波环境自检
                call.respond(toUserEnvs(it.id.id!!, envService.readIdAndVisitableRoleName()))
            }
        }
    }
}
