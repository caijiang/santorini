package io.santorini.console.model

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.Query


/**
 * @author CJ
 */
interface Pageable {
    val limit: Int?
    val offset: Int?
    fun toPageRequest(): PageRequest? {
        return limit?.let { PageRequest(it, offset ?: 0) }
    }
}

fun <R> Query.mapToPage(request: PageRequest, transform: (ResultRow) -> R): PageResult<R> {
    val cq = this.copy()

    return PageResult(
        limit(request.limit).offset(request.offset.toLong()).map(transform),
        cq.count()
    )
}