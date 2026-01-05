package io.santorini.console.schema

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.santorini.OAuthPlatformUserDataAuditResult
import io.santorini.test.mockOAuthPlatformUserData
import io.santorini.test.mockServiceAccount
import io.santorini.tools.database
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MySQLContainer

/**
 * @author CJ
 */
class EnvWikiServiceTest {

    companion object {
        private val mysql = MySQLContainer("mysql:8.0").apply { start() }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            mysql.stop()
        }
    }

    @Test
    fun createWiki() {
        val testEnv = EnvData(
            id = "test", name = "test", production = false
        )
        runTest {
            val envService = EnvService(mysql.database)
            val userRoleService = UserRoleService(mysql.database, mockk(), mockk(), envService)
            val service = EnvWikiService(mysql.database)

            envService.create(
                testEnv
            )
            val userData = userRoleService.oAuthPlatformUserDataToSiteUserData(
                mockOAuthPlatformUserData(), OAuthPlatformUserDataAuditResult.Manager, mockServiceAccount()
            )
            service.createWiki(
                userData.id, testEnv.id!!, EnvWikiContent(
                    title = "T1",
                    content = "文本1",
                )
            )

            service.listByEnv(testEnv.id!!).map {
                it.title to it.content
            } shouldBe listOf("T1" to "文本1")

            service.createWiki(
                userData.id, testEnv.id!!, EnvWikiContent(
                    title = "T2",
                    content = "文本2",
                )
            )

            service.listByEnv(testEnv.id!!).map {
                it.title to it.content
            } shouldBe listOf("T1" to "文本1", "T2" to "文本2")

            service.editWiki(
                userData.id, testEnv.id!!, "T1", EnvWikiContent(
                    title = "", content = "文本3"
                )
            )

            service.listByEnv(testEnv.id!!).map {
                it.title to it.content
            } shouldBe listOf("T1" to "文本3", "T2" to "文本2")

            service.removeWiki(userData.id, testEnv.id!!, "T1")

            service.listByEnv(testEnv.id!!).map {
                it.title to it.content
            } shouldBe listOf("T2" to "文本2")

            service.listByEnv(testEnv.id!!, true).map {
                it.title to it.removed
            } shouldBe listOf("T1" to true, "T2" to false)

        }
    }
}