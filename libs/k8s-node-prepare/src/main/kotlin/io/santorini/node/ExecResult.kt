package io.santorini.node

/**
 * @author CJ
 */
data class ExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)
