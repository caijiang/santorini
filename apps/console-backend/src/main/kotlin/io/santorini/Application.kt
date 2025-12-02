package io.santorini

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.santorini.console.configureConsole
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database

private val logger = KotlinLogging.logger {}
fun main(args: Array<String>) {
    // 它的存在主要是保护 api server
    io.ktor.server.cio.EngineMain.main(args)
}

/**
 * 模块入口
 */
fun Application.consoleModule() {
    consoleModuleEntry()
}

object EnvMysqlData {
    private val host: String? = System.getenv("MYSQL_HOST")?.noEmpty()
    private val port: String? = System.getenv("MYSQL_PORT")?.noEmpty()
    private val user: String? = System.getenv("MYSQL_USER")?.noEmpty()
    private val password: String? = System.getenv("MYSQL_PASSWORD")?.noEmpty()
    private val database: String? = System.getenv("MYSQL_DATABASE")?.noEmpty()

    private fun String?.noEmpty(): String? = if (isNullOrEmpty()) null else this

    val url: String?
        get() {
            if (host == null || port == null || user == null || password == null || database == null) {
                logger.warn {
                    "环境没有设置 MYSQL 信息"
                }
                return null
            }
            return String.format(
                "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true",
                host,
                port,
                database
            )
        }
    val jdbcUser: String? get() = url?.let { user }
    val jdbcPassword: String? get() = url?.let { password }
}

fun Application.consoleModuleEntry(
    httpClient: HttpClient = HttpClient(Apache) {
        install(ContentNegotiation) {
            json(Json)
//            jackson()
        }
    },
    kubernetesClient: KubernetesClient = KubernetesClientBuilder().build(),
    audit: OAuthPlatformUserDataAudit = EnvOAuthPlatformUserDataAudit,
    database: Database = Database.connect(
        url = EnvMysqlData.url ?: "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        user = EnvMysqlData.jdbcUser ?: "root",
        password = EnvMysqlData.jdbcPassword ?: "",
//        driver = "org.h2.Driver",
    )
) {
    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
        json(Json)
    }
    //    configureSerialization()
    configureSecurity(httpClient, kubernetesClient, audit)
    configureHTTP()
    configureRouting()
    configureKubernetes(kubernetesClient)
    configureConsole(database, kubernetesClient)
}
