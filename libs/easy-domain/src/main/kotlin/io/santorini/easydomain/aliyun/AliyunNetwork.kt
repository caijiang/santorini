package io.santorini.easydomain.aliyun

import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.easydomain.aliyun.AliyunNetworkDetector.TIMEOUT
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * 阿里云 OpenAPI 接入点的网络类型。
 *
 * 同一个 region 官方给了两个接入点：公网接入点（如 `alb.cn-hangzhou.aliyuncs.com`）与
 * VPC 接入点（如 `alb-vpc.cn-hangzhou.aliyuncs.com`）。后者只在**同 region 的 VPC 内**
 * 能解析、能连通，走它不消耗公网带宽、也不受公网链路抖动影响。
 *
 * 注意这不是"快一点/慢一点"的选择：选错是**直接连不上**，所以不能拍脑袋猜。
 */
enum class AliyunNetworkKind {
    PUBLIC,
    VPC,
    ;

    companion object {
        /**
         * 解析 [ENV_NETWORK] 这类显式配置。
         *
         * @return `null` 表示"按 [AliyunNetworkDetector] 自动探测"
         * @throws IllegalStateException 值无法识别时。宁可启动就失败，也不要静默跑到错接入点上
         */
        fun of(raw: String?): AliyunNetworkKind? = when (raw?.trim()?.lowercase()) {
            null, "", "auto" -> null
            "vpc", "intranet" -> VPC
            "public", "internet" -> PUBLIC
            else -> throw IllegalStateException("无法识别的网络类型 `$raw`（可选 auto/vpc/public）")
        }

        /** 显式指定网络类型的环境变量，见 [of] */
        const val ENV_NETWORK = "EASY_DOMAIN_ALIYUN_NETWORK"
    }
}

/**
 * 进程所处的阿里云网络环境，由 [AliyunNetworkDetector] 从实例元数据读出来。
 *
 * @param regionId 实例所在 region（`cn-hangzhou` 这类）；读不到（不在阿里云内）时为 null
 * @param inVpc    实例是不是 VPC 类型
 */
data class AliyunNetwork(val regionId: String?, val inVpc: Boolean) {

    /**
     * 目标资源在 [targetRegion] 时，本进程应当使用哪种接入点。
     *
     * VPC 接入点是**地域级**的：`alb-vpc.cn-hangzhou.aliyuncs.com` 从上海的 VPC 里解析不出来。
     * 所以只有"自己在 VPC 里"且"与目标同 region"才用内网接入点，其余一律公网——
     * 宁可慢一点，不能连不上。
     */
    fun kindFor(targetRegion: String): AliyunNetworkKind =
        if (inVpc && regionId == targetRegion) AliyunNetworkKind.VPC else AliyunNetworkKind.PUBLIC

    companion object {
        /** 探测不出来：不在阿里云内、元数据服务被网络策略挡住、或超时 */
        val UNKNOWN = AliyunNetwork(null, false)
    }
}

/**
 * 把元数据的 `region-id` / `network-type` 解读成网络环境。
 *
 * `network-type` 是**只有 VPC 类型实例才有**的字段（经典网络实例读不到），值就是 `vpc`；
 * 所以"读得到且等于 vpc"才算在 VPC 内。
 */
fun aliyunNetworkOf(regionId: String?, networkType: String?): AliyunNetwork = AliyunNetwork(
    regionId = regionId?.trim()?.takeIf { it.isNotEmpty() },
    inVpc = networkType?.trim()?.equals("vpc", ignoreCase = true) == true,
)

/**
 * 数字证书管理服务（CAS）的 VPC 接入点，命名规则来自官方「服务接入点」文档：
 * `cas-vpc.<region>.aliyuncs.com`。
 *
 * 这里**只**给 VPC 的：公网接入点交给 SDK 自己解析更可靠——它自带的映射里
 * `cas.aliyuncs.com` 是不带 region 的，我们抄一份表迟早抄错。
 *
 * ALB 的 VPC 接入点不在这里，它由 SDK 的 `endpointType` 机制算出来（理由见
 * [io.santorini.easydomain.CasCertificateUploader]）。
 */
fun casVpcEndpoint(region: String): String = "cas-vpc.$region.aliyuncs.com"

/**
 * 探测进程所在的阿里云网络环境。
 *
 * 只认 ECS 实例元数据服务 [METADATA_BASE]：它是阿里云在实例内固定提供的链路本地地址，
 * 读得到就说明"人在阿里云内"，读不到（本机调试、非阿里云集群、被 NetworkPolicy 挡住）
 * 就按公网处理。
 */
object AliyunNetworkDetector {

    /** ECS 实例元数据服务的固定地址 */
    const val METADATA_BASE = "http://100.100.100.200/latest/meta-data"

    /** 内网地址，超时必须短：探测只是锦上添花，不能拖慢启动 */
    private val TIMEOUT: Duration = Duration.ofMillis(800)

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
    }

    @Volatile
    private var cached: AliyunNetwork? = null

    /**
     * 探测当前网络环境。**会阻塞**（首次调用最多 [TIMEOUT] 两倍），但进程内只探一次：
     * 网络环境不会在运行期变，而证书同步的装配是每次心跳都跑一遍的。
     */
    fun detect(): AliyunNetwork = cached ?: probe().also { cached = it }

    private fun probe(): AliyunNetwork {
        val regionId = read("region-id")
        val network = aliyunNetworkOf(regionId, read("network-type"))
        if (regionId == null) {
            logger.info { "读不到阿里云实例元数据，按公网接入点访问 OpenAPI" }
        } else {
            logger.info {
                "阿里云实例元数据：region=$regionId，网络类型=${if (network.inVpc) "vpc" else "非 vpc"}"
            }
        }
        return network
    }

    private fun read(path: String): String? = runCatching {
        val request = HttpRequest.newBuilder(URI.create("$METADATA_BASE/$path"))
            .timeout(TIMEOUT)
            .GET()
            .build()
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            .takeIf { it.statusCode() == 200 }
            ?.body()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }.onFailure {
        logger.debug { "读实例元数据 $path 失败：${it.message}" }
    }.getOrNull()
}
