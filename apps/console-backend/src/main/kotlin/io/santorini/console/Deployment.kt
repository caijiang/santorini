@file:OptIn(ExperimentalUuidApi::class)

package io.santorini.console

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.schema.DeploymentDeployData
import io.santorini.schema.DeploymentResource
import io.santorini.schema.DeploymentService
import io.santorini.withAuthorization
import org.koin.ktor.ext.get
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid


private val logger = KotlinLogging.logger {}

internal fun Application.configureConsoleDeployment() {
    val service = get<DeploymentService>()
    // 一般人员可以读取 env
    routing {
        post<DeploymentResource.Deploy> { deployData ->
            // 这个得检查权限
            withAuthorization {
                val data = call.receive<DeploymentDeployData>()
                logger.info {
                    "提交部署数据:$it, data: $data"
                }

                // 因为这玩意儿是一成不变的，所以只能新增，无法修改
                try {
                    // 然后操作 kubernetes
                    call.respond(service.deploy(deployData, data).toKotlinUuid())
                } catch (e: Exception) {
                    logger.info(e) { "处理部署时" }
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
        }
        delete<DeploymentResource.Id> { target ->
            withAuthorization {
                logger.info {
                    "准备删除失败的部署:$it, data: $target"
                }
                if (service.deleteFailedDeploy(UUID.fromString(target.id)) > 0) {
                    call.respond(HttpStatusCode.NoContent)
                } else
                    call.respond(HttpStatusCode.BadRequest)
            }
        }
        put<DeploymentResource.IdAndName> { idAndName ->
            if (idAndName.name != "targetResourceVersion") {
                call.respond(HttpStatusCode.NotFound)
            } else
                withAuthorization {
                    val data = call.receive<String>()
                    logger.info {
                        "补充提交部署数据:$it, data: $data"
                    }

                    // 因为这玩意儿是一成不变的，所以只能新增，无法修改
                    try {
                        // 然后操作 kubernetes
                        if (service.updateTargetResourceVersion(UUID.fromString(idAndName.id), data) > 0) {
                            call.respond(HttpStatusCode.NoContent)
                        } else
                            call.respond(HttpStatusCode.BadRequest)
                    } catch (e: Exception) {
                        logger.info(e) { "处理部署时" }
                        call.respond(HttpStatusCode.BadRequest)
                    }
                }
        }
    }
}