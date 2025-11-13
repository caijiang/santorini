package buildsrc.convention.santorini.tasks

/**
 * @author CJ
 */
data class SftpSyncConfig(
    val username: String?,
    val host: String?,
    val port: Int?,
    val remoteDir: String?,
    /**
     * 需要执行的指令
     */
    val executeCommands: List<String>?,
    val password: String?,
    val privateKeyPath: String?,
    val dryRun: Boolean?,
) {
    fun mergeFromParent(parent: SftpSyncConfig?): SftpSyncConfig {
        if (parent == null) {
            return this
        }
        return copy(
            username = username ?: parent.username,
            host = host ?: parent.host,
            port = port ?: parent.port,
            remoteDir = remoteDir ?: parent.remoteDir,
            executeCommands = executeCommands ?: parent.executeCommands,
            password = password ?: parent.password,
            privateKeyPath = privateKeyPath ?: parent.privateKeyPath,
            dryRun = dryRun ?: parent.dryRun
        )
    }
}
