package io.santorini.console

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.santorini.console.schema.HpaStatusService
import io.santorini.console.schema.UserRoleService
import io.santorini.withCallAuthorization
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.koin.ktor.ext.inject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

//val hpaUpdates = MutableSharedFlow<HpaView>()
//
//// K8s Watch 更新时：
//hpaUpdates.emit(newState)
//
//// SSE：
//hpaUpdates.collect { send(...) }
// 这个方式肯定很好，但是需要路由控制 hpaStatus 只能到拿到租约的pod
private val duration: Duration = 5.seconds

internal fun Application.configureHpaLive() {
    val userService = inject<UserRoleService>().value
    val service = inject<HpaStatusService>().value

    routing {
        // 因为 sse 的设计是不允许太多同时链接，所以只能选择一个通道把所有变化都写进去
        sse("/hpaStatus", serialize = { typeInfo, it ->
            val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
            Json.encodeToString(serializer, it)
        }) {
            try {
                call.withCallAuthorization {
                    // 第一次全推
                    val services = userService.readServiceRoleByUser(it.id)
                        .keys
                    val envs = userService.toUserEnvs(it.id)

                    var lastQueryTime = Clock.System.now()
                    // 10 分钟
                    val data = service.queryTimelineSince(lastQueryTime.minus(10.minutes), services, envs.toSet())
                    logger.debug {
                        "first HpaStatusService.queryTimelineSince (lastQueryTime:$lastQueryTime),(services:$services),(envs:$envs);结果为:$data"
                    }
                    send(data)
                    while (isActive) {
                        delay(duration)
                        val data1 = service.queryTimelineSince(lastQueryTime, services, envs.toSet())
                        logger.debug {
                            "loop HpaStatusService.queryTimelineSince (lastQueryTime:$lastQueryTime),(services:$services),(envs:$envs);结果为:$data1"
                        }
                        send(data1)
                        lastQueryTime = Clock.System.now()
                    }
                }
            } catch (e: Exception) {
                logger.error(e) {
                    "hpaStatus ?"
                }
            }
        }
    }
}