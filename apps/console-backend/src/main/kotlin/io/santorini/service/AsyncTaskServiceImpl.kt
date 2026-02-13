package io.santorini.service

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author CJ
 */
class AsyncTaskServiceImpl(
    workerCount: Int = Runtime.getRuntime().availableProcessors()
) : AutoCloseable, AsyncTaskService {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    private val channel = Channel<suspend () -> Unit>(
        Channel.UNLIMITED
    )

    private val started = AtomicBoolean(false)

    init {
        startWorkers(workerCount)
    }

    private fun startWorkers(n: Int) {
        if (!started.compareAndSet(false, true)) return

        repeat(n) {
            scope.launch {
                for (task in channel) {
                    try {
                        task()
                    } catch (e: Exception) {
                        // 这里统一异常处理
                        println("Task error: ${e.message}")
                    }
                }
            }
        }
    }

    override fun submit(task: suspend () -> Unit) {
        channel.trySend(task)
    }

    override fun close() {
        scope.cancel()
        channel.close()
    }
}