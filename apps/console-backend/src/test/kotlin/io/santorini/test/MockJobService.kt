package io.santorini.io.santorini.test

import io.github.caijiang.common.job.worker.PersistentJob
import io.github.caijiang.common.job.worker.ScheduleJobService
import io.github.caijiang.common.job.worker.TemporaryJob
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

/**
 * @author CJ
 */
object MockJobService : ScheduleJobService {
    private val logger = KotlinLogging.logger {}
    override fun submitPersistentJob(cron: String, job: PersistentJob, timezone: TimeZone, springCronSeconds: String) {
        logger.info { "Submitting job $cron to $job" }
    }

    override fun submitTemporaryJob(job: TemporaryJob) {
        logger.info { "Submitting job $job" }
    }
}