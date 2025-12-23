package io.santorini

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.santorini.console.configureConsole
import io.santorini.schema.*
import io.santorini.scope.AppBackgroundScope
import io.santorini.well.StatusException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

private val ktLogger = KotlinLogging.logger {}
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
                ktLogger.warn {
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
    install(Koin) {
        slf4jLogger()
        modules(module {
            single {
                AppBackgroundScope()
            }
            single {
                kubernetesClient
            }
            single {
                EnvService(database)
            }
            single {
                HostService(database)
            }
            single {
                ServiceMetaService(database, lazy { get() })
            }
            single {
                DeploymentService(database, get(), get())
            }
            single {
                UserRoleService(database, get(), get(), get())
            }
        })
    }
    monitor.subscribe(ApplicationStarted) {
        it.get<AppBackgroundScope>().launch {
            while (isActive) {
                ktLogger.debug { "系统心跳" }
                get<DeploymentService>().heart()
                delay(10.seconds)
            }
        }
    }
    monitor.subscribe(ApplicationStopped) {
        it.get<AppBackgroundScope>().cancel("stop")
    }
    install(StatusPages) {
        exception<StatusException> { call, cause ->
            call.respond(cause.status)
        }
    }
    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
        json(Json)
    }
    install(CallLogging) {
        level = Level.INFO
//        logger = ktLogger
    }
    //    configureSerialization()
    configureSecurity(httpClient, kubernetesClient, audit)
    configureHTTP()
    configureRouting()
    configureKubernetes(kubernetesClient)
    configureConsole(kubernetesClient)
}
