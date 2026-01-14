package io.santorini.node

import io.github.caijiang.common.security.RSAUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.junit.jupiter.api.Disabled
import java.io.IOException
import kotlin.test.Test

/**
 * @author CJ
 */
@Disabled
class CommandExecutorTest {

    private val logger = KotlinLogging.logger {}

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private suspend fun executeWithTestExecutor(work: suspend (CommandExecutor) -> Unit) {
        val target = javaClass.getResourceAsStream("/local-build-ssh.json")?.let {
            json.decodeFromStream<TargetHost>(it)
        }
        if (target == null) {
            println("skipping test")
        } else {
            logger.info { "target: $target" }
            CommandExecutor(
                ip = target.ip,
                proxyJump = target.proxyJump,
                keyPair = setOf(RSAUtils.readKeyPairFromPKCS8(target.privateKeyPemPKCS8))
            ).use {
                work(it)
            }
        }
    }


    @Test
    fun executeCommand() = runTest {
        executeWithTestExecutor {
            it.executeCommand("echo hello") shouldBe ExecResult(
                0, "hello\n", ""
            )

            it.executeCommand("cat /noHereFile") shouldBe ExecResult(
                1,
                "",
                "cat: /noHereFile: No such file or directory\n"
            )

        }
    }

    @Test
    fun joinNode() = runTest {
        executeWithTestExecutor { executor ->
            val generator = CommandGenerator(
                javaClass.getResourceAsStream("/local-build-join.txt")?.bufferedReader()
                    ?.readLine() ?: throw IOException("没有指令文件"),
                javaClass.getResourceAsStream("/local-build-registry.json")?.let {
                    json.decodeFromStream<PrivateImageRegistry>(it)
                }
            )

            generator.generateCommands().forEach {
                executor.executePhase(it)
            }

        }
    }
}