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
import io.santorini.console.schema.DeploymentDeployData
import io.santorini.console.schema.DeploymentResource
import io.santorini.console.schema.DeploymentService
import io.santorini.console.schema.PreDeployResult
import io.santorini.withAuthorization
import java.util.*
import kotlin.uuid.toKotlinUuid
import org.koin.ktor.ext.get as koinGet


private val logger = KotlinLogging.logger {}

internal fun Application.configureConsoleDeployment() {
    val service = koinGet<DeploymentService>()
    // 一般人员可以读取 env
    routing {
        // 所有人的最近发布信息?
        get<DeploymentResource> { resource ->
            // 默认降序
            // 可见环境，可见服务
            val pr = resource.toPageRequest()
            withAuthorization {
                if (pr != null) {
                    call.respond(service.readAsPage(resource, it.id, pr))
                } else {
                    call.respond(service.read(resource, it.id))
                }
            }
        }
        post<DeploymentResource.PreDeploy> { deployData ->
            // 预发布
            withAuthorization {
                val data = call.receive<DeploymentDeployData>()
                try {
                    // 然后操作 kubernetes
                    call.respond(service.preDeploy(deployData, data))
                } catch (e: Exception) {
                    logger.info(e) { "预览部署时" }
                    call.respond(PreDeployResult(warnMessage = e.localizedMessage))
                }
            }
        }
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
                    call.respond(service.deploy(it.id, deployData, data).toKotlinUuid())
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
            if (idAndName.name != "targetGeneration") {
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
                        if (service.updateTargetGeneration(UUID.fromString(idAndName.id), data.toLong()) > 0) {
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