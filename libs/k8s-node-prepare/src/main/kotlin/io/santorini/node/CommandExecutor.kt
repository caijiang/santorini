package io.santorini.node

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.config.hosts.HostConfigEntry
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier
import org.apache.sshd.client.session.ClientSession
import java.security.KeyPair
import java.util.*
import kotlin.coroutines.CoroutineContext

/**
 * 通过 ssh 直接执行指令
 * @author CJ
 */
class CommandExecutor(
    val ip: String,
    /**
     * 用以登录 ssh 服务器的密钥，建议一个就好
     */
    private val keyPair: Set<KeyPair>,
    /**
     * https://en.wikibooks.org/wiki/OpenSSH/Cookbook/Proxies_and_Jump_Hosts
     */
    private val proxyJump: String? = null,
    private val port: Int = 22,
    private val username: String = "root",
    /**
     * 可定制的 ssh 连接器，其默认值就是直接连接
     */
    private val clientSessionFetcher: (SshClient) -> ClientSession = { sshClient ->
        sshClient
            .let {
                if (proxyJump != null) {
                    it.connect(HostConfigEntry("", ip, port, username, proxyJump))
                } else
                    it.connect(username, ip, port)
            }
            .verify(10000)
            .session
    },
    private val coroutineContext: CoroutineContext = Dispatchers.IO
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private var sshClient: SshClient? = null
    private var clientSession: ClientSession? = null

    /**
     * 执行一个阶段的任务，失败就异常
     */
    suspend fun executePhase(phase: Phase) {
        logger.info { "${ip}:准备执行阶段: ${phase.name}" }
        phase.commands.forEach { command ->
            if (command.conditionToRun?.let { it1 -> it1(this) } != false) {
                val result = executeCommand(command.cmd)
                if (!command.allowExitCode.contains(result.exitCode)) {
                    logger.error { "指令:${command.cmd}不符合结果:${result}" }
                    throw Exception(result.toString())
                }
                logger.debug { "指令:${command.cmd}结果:${result}" }
            } else {
                logger.info { "指令:${command.cmd} 的前置条件没有满足，跳过" }
            }
        }
    }

    suspend fun executeCommand(command: String): ExecResult {
        val session = openSession()
        val channel = session.createExecChannel(command)
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        channel.use {
            withContext(coroutineContext) {
                it.open().verify()
            }

            withContext(Dispatchers.Default) {
                val stdoutJob = launch(coroutineContext) {
                    channel.invertedOut.bufferedReader().useLines { lines ->
                        lines.forEach { stdout.appendLine(it) }
                    }
                }

                val stderrJob = launch(coroutineContext) {
                    channel.invertedErr.bufferedReader().useLines { lines ->
                        lines.forEach { stderr.appendLine(it) }
                    }
                }

                withContext(coroutineContext) {
                    channel.waitFor(
                        EnumSet.of(ClientChannelEvent.CLOSED),
                        0L
                    )
                }

                stdoutJob.join()
                stderrJob.join()
            }

            return ExecResult(
                exitCode = channel.exitStatus,
                stdout = stdout.toString(),
                stderr = stderr.toString()
            )
        }
    }

    private suspend fun openSession(): ClientSession {
        if (clientSession != null) return clientSession!!
        val client = openClient()
        clientSession = clientSessionFetcher(client).apply {
            keyPair.forEach { addPublicKeyIdentity(it) }
            withContext(coroutineContext) {
                auth().verify(10000)
            }
        }
        return clientSession!!
    }

    private suspend fun openClient(): SshClient {
        if (sshClient != null) return sshClient!!
        sshClient = SshClient.setUpDefaultClient().apply {
            serverKeyVerifier = AcceptAllServerKeyVerifier.INSTANCE
            withContext(coroutineContext) {
                start()
            }
        }
        return sshClient!!
    }

    override fun close() {
        try {
            clientSession?.close()
        } catch (ignored: Exception) {
        }
        try {
            sshClient?.close()
        } catch (ignored: Exception) {
        }
    }

}