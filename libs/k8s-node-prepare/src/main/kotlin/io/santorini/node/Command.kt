package io.santorini.node

/**
 * 一条指令，一般要求返回 0
 * @author CJ
 */
data class Command(
    /**
     * 为了防止歧义，特地使用简写
     */
    val cmd: String,
    /**
     * 允许的指令结果
     */
    val allowExitCode: Set<Int> = setOf(0),
    /**
     * 执行该指令的前置条件
     */
    val conditionToRun: (suspend (CommandExecutor) -> Boolean)? = null,
)
