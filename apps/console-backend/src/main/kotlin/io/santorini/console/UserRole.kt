package io.santorini.console

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
internal fun Application.configureConsoleUser(database: Database) {
    val service = UserRoleService(database)
    val envService = EnvService(database)

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
                }) { _ ->
                val current = toUserEnvs(it.id.id!!)
                if (current.contains(targetEnvId)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    service.addUserEnv(it.id.id, targetEnvId)
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
                service.readServiceRoleByUser(user.id)
//                call.respond(toUserEnvs(it.id.id!!))
                TODO("Not yet implemented")
            }
        }
    }
}
