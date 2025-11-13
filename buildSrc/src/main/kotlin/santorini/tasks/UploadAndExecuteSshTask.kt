package buildsrc.convention.santorini.tasks

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.util.removeSuffixIfPresent
import java.io.File
import javax.inject.Inject


/**
 * 负责上传到特定 sftp然后执行什么东西
 *
 * 它可以优先从当前目录的 `local-upload-sftp-config.json` 然后是 root目录的 `local-upload-sftp-config.json`
 * @author CJ
 */
abstract class UploadAndExecuteSshTask : DefaultTask() {
    @get:Inject
    abstract val layout: ProjectLayout
//    @get:Inject
//    abstract val fs: FileSystemOperations
    /**
     * 要传递的目标文件,目标目录。
     * 如何 算出 remoteDir;  固定的 remoteDir + 所有文件公共的 parent
     */
    @get:InputFiles
    abstract val targetFiles: ConfigurableFileCollection

    @get:InputDirectory
    @get:Optional
    abstract val rootDir: DirectoryProperty

    private fun toConfig(file: RegularFile): SftpSyncConfig? {
        if (!file.asFile.exists()) {
            return null
        }
        val json = JsonSlurper().parseText(file.asFile.readText()) as Map<*, *>
//        logger.warn("Parsing config from ${json["executeCommands"]} ${json["executeCommands"]?.javaClass}")
        @Suppress("UNCHECKED_CAST")
        return SftpSyncConfig(
            json["username"] as String?,
            json["host"] as String?,
            json["port"] as Int?,
            json["remoteDir"] as String?,
            (json["executeCommands"] as List<String>?),
            json["password"] as String?,
            json["privateKeyPath"] as String?,
            json["dryRun"] as Boolean?,
        )
    }

    private fun ggc(): SftpSyncConfig? {
        val projectLevel = toConfig(layout.projectDirectory.file("local-upload-sftp-config.json"))
        if (rootDir.isPresent) {
            val rootLevel = toConfig(rootDir.get().file("local-upload-sftp-config.json"))
            if (projectLevel == null) {
                return rootLevel
            }
            return projectLevel.mergeFromParent(rootLevel)
        }
        return projectLevel
    }

    @TaskAction
    fun consume() {
        val configJson = ggc()
        if (configJson == null) {
            logger.warn("没有配置 local-upload-sftp-config.json 跳过上传")
            return
        }
        val maxParent = targetFiles.files.toMaxParent()
        // 我们就认为 maxParent 就是根目录
        logger.debug("maxParent:${maxParent.absolutePath}")

        // 使用 JSch 上传
        val jsch = JSch()
        configJson.privateKeyPath?.let {
            jsch.addIdentity(it)
        }
        val session = if (configJson.port != null) jsch.getSession(
            configJson.username
                ?: throw InvalidUserDataException("local-upload-sftp-config.json 需要添加有效的 username 字段"),
            configJson.host ?: throw InvalidUserDataException("local-upload-sftp-config.json 需要添加有效的 host 字段"),
            configJson.port
        ) else jsch.getSession(
            configJson.username
                ?: throw InvalidUserDataException("local-upload-sftp-config.json 需要添加有效的 username 字段"),
            configJson.host ?: throw InvalidUserDataException("local-upload-sftp-config.json 需要添加有效的 host 字段")
        )
        session.setConfig("StrictHostKeyChecking", "no")
        session.connect()
        try {
            logger.warn("connected to ${configJson.host}")
            val channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            try {
//                channel.cd(
//                    configJson.remotePath
//                        ?: throw InvalidUserDataException("local-upload-sftp-config.json 需要添加有效的 remotePath 字段")
//                )

                targetFiles.files.forEach { file ->
                    // configJson.remotePath /
                    val remoteName =
                        "${configJson.remoteDir?.removeSuffixIfPresent("/") ?: throw InvalidUserDataException("local-upload-sftp-config.json 需要添加有效的 remotePath 字段")}${
                            file.absolutePath.removePrefix(
                                maxParent.absolutePath
                            )
                        }"
                    if (configJson.dryRun == true) {
                        logger.warn("\uD83D\uDCE6 Uploading  ${file.absolutePath} to $remoteName")
                    } else {
                        logger.info("\uD83D\uDCE6 Uploading  ${file.absolutePath} to $remoteName")
                    }
                    if (configJson.dryRun != true) {
                        channel.put(file.absolutePath, remoteName)
                        logger.warn("✅ Upload completed:${file.absolutePath} to $remoteName")
                    }
                }

            } finally {
                channel.disconnect()
            }

            configJson.executeCommands?.forEach { command ->
                val exec = session.openChannel("exec") as ChannelExec
                if (configJson.dryRun == true) {
                    logger.warn("executing $command")
                } else {
                    logger.info("executing $command")
                }
                if (configJson.dryRun != true) {
                    exec.setCommand(command)
                    exec.connect()
                    try {
                        val output = exec.inputStream.bufferedReader().readText()
                        logger.info("Remote output:$output")
                    } finally {
                        exec.disconnect()
                    }
                }
            }

        } finally {
            session.disconnect()
        }

    }

}


private fun Set<File>.toMaxParent(): File {
    // InvalidUserDataException
    // GradleException
    var currentParent = this.firstOrNull()?.parentFile ?: throw InvalidUserDataException("至少要制定一个目标文件")
    while (true) {
        if (all { it.startsWith(currentParent) }) {
            return currentParent
        }
        currentParent = currentParent.parentFile
    }
}
