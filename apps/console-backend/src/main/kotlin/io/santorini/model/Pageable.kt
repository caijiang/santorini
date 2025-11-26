package io.santorini.model

import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow

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