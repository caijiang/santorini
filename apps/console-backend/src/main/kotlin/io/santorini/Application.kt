package io.santorini

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.github.caijiang.common.job.scheduler.KubernetesJobScheduler
import io.github.caijiang.common.job.scheduler.Scheduler
import io.github.caijiang.common.job.worker.PersistentJob
import io.github.caijiang.common.job.worker.ScheduleJobService
import io.github.caijiang.common.job.worker.TemporaryJob
import io.github.caijiang.common.job.worker.bean.SchedulerScheduleJobService
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
import io.santorini.console.schema.*
import io.santorini.kubernetes.KubernetesClientServiceImpl
import io.santorini.scope.AppBackgroundScope
import io.santorini.service.ImageService
import io.santorini.service.KubernetesClientService
import io.santorini.well.StatusException
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.parameter.ParametersHolder
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level

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
    kubernetesClientService: KubernetesClientService = KubernetesClientServiceImpl(kubernetesClient),
    audit: OAuthPlatformUserDataAudit = EnvOAuthPlatformUserDataAudit,
    database: Database = Database.connect(
        url = EnvMysqlData.url ?: "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        user = EnvMysqlData.jdbcUser ?: "root",
        password = EnvMysqlData.jdbcPassword ?: "",
//        driver = "org.h2.Driver",
    ),
    imageServiceLoader: Scope.(ParametersHolder) -> ImageService = {
        ImageService(get(), get())
    }
) {
    val app = this
    install(Koin) {
        slf4jLogger()
        modules(module {
            single {
                kubernetesClientService
            }
            single {
                httpClient
            }
            single {
                AppBackgroundScope()
            }
            single {
                kubernetesClient
            }
            single<Scheduler> {
                KubernetesJobScheduler(kubernetesClient)
            }
            single {
                imageServiceLoader(it)
            }
            single {
                SystemStringService(database)
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
                DeploymentService(database, get(), get(), get())
            }
            single {
                UserRoleService(database, get(), get(), get())
            }
            single {
                EnvWikiService(database)
            }
            single<ScheduleJobService> {
                SchedulerScheduleJobService(
                    JobRunner(app), get()
                )
            }
        })
    }
    app.get<ScheduleJobService>().submitPersistentJob("* * * * *", object : PersistentJob {
        override val name: String
            get() = "santorini-$JOB_HEARTBEAT"
        override val parameters: Map<String, String>
            get() = emptyMap()
        override val type: String
            get() = JOB_HEARTBEAT
    })
    if ("true" == System.getenv("TEST"))
        app.get<ScheduleJobService>().submitTemporaryJob(object : TemporaryJob {
            override val parameters: Map<String, String>
                get() = mapOf()
            override val type: String
                get() = JOB_FINE
        })
//    monitor.subscribe(ApplicationStarted) {
//        try {
//            val deploymentService = get<DeploymentService>()
//            it.get<AppBackgroundScope>().launch {
//                while (isActive) {
//                    ktLogger.debug { "系统心跳" }
//                    try {
//                        deploymentService.heart()
//                    } catch (e: Exception) {
//                        ktLogger.warn(e) {
//                            "业务问题"
//                        }
//                    }
//                    delay(10.seconds)
//                }
//            }
//        } catch (e: Throwable) {
//            ktLogger.warn(e) { "启动时？github-action中？" }
//        }
//    }
    monitor.subscribe(ApplicationStopped) {
        try {
            it.get<AppBackgroundScope>().cancel("stop")
        } catch (e: Throwable) {
            ktLogger.warn(e) { "关闭时，不太关心" }
        }
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
