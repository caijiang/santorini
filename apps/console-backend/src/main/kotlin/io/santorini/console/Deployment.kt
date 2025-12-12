package io.santorini.console

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.santorini.schema.DeploymentDeployData
import io.santorini.schema.DeploymentResource
import io.santorini.schema.DeploymentService
import io.santorini.withAuthorization
import org.koin.ktor.ext.get


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
                    service.deploy(deployData, data)
                    // 然后操作 kubernetes
                    call.respond(HttpStatusCode.OK)
                } catch (e: Exception) {
                    logger.info(e) { "处理部署时" }
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
        }
    }
}