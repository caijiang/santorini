package io.santorini.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test

/**
 * @author CJ
 */
class AsyncTaskServiceImplTest {
    private val logger = KotlinLogging.logger {}

    @Test
    fun `async task works`() {
        val service = AsyncTaskServiceImpl()
        runTest {
            service.submit {
                logger.warn { "Async task started" }
                withContext(Dispatchers.IO) {
                    logger.warn { "task in IO" }
                }
                withContext(Dispatchers.Default) {
                    logger.warn { "task in Default" }
                }
//                withContext(Dispatchers.Main) {
//                    logger.warn { "task in Main" }
//                }
                withContext(Dispatchers.Unconfined) {
                    logger.warn { "task in Unconfined" }
                }
            }
        }
        service.close()
    }
}