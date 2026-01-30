package io.santorini

import io.github.caijiang.common.job.worker.JobTypeRunner
import io.github.caijiang.common.job.worker.SerializableJob
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.santorini.console.schema.DeploymentService
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.get

const val JOB_HEARTBEAT = "heartbeat"
const val JOB_FINE = "finely"

/**
 *
 * @author CJ
 */
class JobRunner(
    private val application: Application
) : JobTypeRunner {
    private val ktLogger = KotlinLogging.logger {}
    override fun quitApplication(): Boolean {
        application.monitor.raise(ApplicationStopping, application)
        application.engine.stop()
        return true
    }

    override fun run(job: SerializableJob) {
        if (job.type == JOB_HEARTBEAT) {
            val deploymentService = application.get<DeploymentService>()
            runBlocking {
                ktLogger.debug { "系统心跳" }
                try {
                    deploymentService.heart()
                } catch (e: Exception) {
                    ktLogger.warn(e) {
                        "业务问题"
                    }
                }
            }
            return
        }
        if (job.type == JOB_FINE) {
            ktLogger.info { "that's fine!" }
            return
        }
        TODO("Not yet implemented for ${job.type}")
    }
}