package io.santorini.service

/**
 * @author CJ
 */
interface AsyncTaskService : AutoCloseable {
    /**
     * 提交任务
     */
    fun submit(task: suspend () -> Unit)
}