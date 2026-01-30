package io.santorini

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.santorini.io.santorini.test.MockJobService
import io.santorini.tools.database
import org.junit.jupiter.api.AfterAll
import org.testcontainers.containers.MySQLContainer
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    companion object {
        private val mysql = MySQLContainer("mysql:8.0").apply { start() }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            mysql.stop()
        }
    }

    @Test
    fun databaseColumns() = testApplication {
        mysql.createConnection("")
            .use { connection ->
                connection.createStatement().use {
                    it.execute("CREATE TABLE IF NOT EXISTS ServiceMetas (id VARCHAR(63) PRIMARY KEY, `name` VARCHAR(50) NOT NULL, `type` VARCHAR(10) NOT NULL, createTime TIMESTAMP(6) NOT NULL)")
                }
            }
        application {
            consoleModuleEntry(
                database = mysql.database,
                scheduleJobServiceLoader = { _, _ -> MockJobService }
            )
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

}