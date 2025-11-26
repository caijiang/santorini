package io.santorini.model

import kotlinx.serialization.Serializable


/**
 * @author CJ
 */
@Serializable
data class PageResult<T>(
    val records: List<T>,
    val total: Long
)
