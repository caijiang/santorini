package io.santorini.console

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.model.ServiceRole
import io.santorini.schema.EnvService
import io.santorini.schema.UserResource
import io.santorini.schema.UserRoleService
import io.santorini.withAuthorization
import org.koin.ktor.ext.inject
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
internal fun Application.configureConsoleUser() {
    val service = inject<UserRoleService>().value
    val envService = inject<EnvService>().value

    /**
     * @return [EnvService.Envs.id]
     */
    suspend fun toUserEnvs(userId: Uuid): List<String> {
        val user = service.userById(userId.toJavaUuid())
        return if (user == null) {
            listOf()
        } else if (user.grantAuthorities.root) {
            envService.readId()
        } else {
            service.envIdsByUserId(userId)
        }
    }
    routing {
        get<UserResource> {
            withAuthorization(
                {
                    it.grantAuthorities?.users == true
                }) { _ ->
                val pr = it.toPageRequest()
                if (pr != null) {
                    call.respond(service.readUserAsPage(it, pr))
                } else {
                    call.respond(service.readUser(it))
                }
            }
        }
        delete<UserResource.Id.Envs.One> { target ->
            withAuthorization(
                {
                    val manageEnvs = toUserEnvs(
                        it.id
                    )
                    it.grantAuthorities?.users == true && it.grantAuthorities.envs && manageEnvs.contains(
                        target.env
                    )
                }) {
                service.removeUserEnv(target.parent.id.id!!, target.env)
                call.respond(HttpStatusCode.NoContent)
            }
        }
        post<UserResource.Id.Envs> {
            val targetEnvId = call.receive<String>()
            logger.info { "分配环境${targetEnvId}给${it.id.id}" }
            withAuthorization(
                {
                    val manageEnvs = toUserEnvs(
                        it.id
                    )
                    it.grantAuthorities?.users == true && it.grantAuthorities.envs && manageEnvs.contains(
                        targetEnvId
                    )
                }) { cu ->
                val current = toUserEnvs(it.id.id!!)
                if (current.contains(targetEnvId)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    service.addUserEnv(it.id.id, targetEnvId, cu.id)
                    call.respond(HttpStatusCode.Created)
                }
            }
        }
        get<UserResource.Id.Envs> {
            withAuthorization(
                {
                    it.grantAuthorities?.users == true && it.grantAuthorities.envs
                }) { _ ->
                // 环境的 搞一波环境自检
                call.respond(toUserEnvs(it.id.id!!))
            }
        }
        get<UserResource.Id.Services> {
            withAuthorization(
                {
                    it.grantAuthorities?.users == true && it.grantAuthorities.roles
                }) { user ->
                // 环境的 搞一波环境自检
                val currentUserOwners = service.readServiceRoleByUser(user.id)
                val target = service.readServiceRoleByUser(it.id.id!!)
                call.respond(target.filterKeys {
                    currentUserOwners.containsKey(it)
                })
            }
        }
        delete<UserResource.Id.Services.One> { target ->
            withAuthorization(
                {
                    it.grantAuthorities?.users == true && it.grantAuthorities.roles && it.grantAuthorities.assigns
                }) {
                service.removeUserServiceRole(target.parent.id.id!!, target.service, target.role)
                call.respond(HttpStatusCode.NoContent)
            }
        }
        post<UserResource.Id.Services> {
            withAuthorization(
                {
                    it.grantAuthorities?.users == true && it.grantAuthorities.roles && it.grantAuthorities.assigns
                }) { user ->
                // 环境的 搞一波环境自检
                val currentUserOwners = service.readServiceRoleByUser(user.id)
                val (serviceId, role) = call.receive<Pair<String, ServiceRole>>()
                // 必须拥有该权限或者是 Owner
                if (currentUserOwners[serviceId]?.contains(ServiceRole.Owner) != true && currentUserOwners[serviceId]?.contains(
                        role
                    ) != true
                ) {
                    call.respond(HttpStatusCode.Forbidden)
                } else {
                    service.assignServiceRole(it.id.id!!, serviceId, role, user.id)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
