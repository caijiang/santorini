package io.santorini.node

/**
 * 阶段
 * @author CJ
 */
data class Phase(
    val name: String,
    val commands: List<Command>,
)
