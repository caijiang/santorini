package io.santorini.easydomain

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.*
import javax.naming.NameNotFoundException
import javax.naming.NamingException
import javax.naming.directory.InitialDirContext

/**
 * DNS 解析的可注入抽象——单测里用假实现，生产用系统解析。
 */
interface DnsResolver {
    /**
     * @return 解析到的所有地址；解析失败返回空集合
     */
    fun resolveA(hostname: String): List<InetAddress>

    /**
     * 本名的 CNAME **第一跳**指向。只查一跳，不跟链——跟链是调用方的事，
     * 见 [DnsScopeMatcher]。
     *
     * @return CNAME 目标（已去掉尾点，大小写保持 DNS 原样）；没有 CNAME、域名不存在或解析失败返回 null
     */
    fun resolveCname(hostname: String): String?
}

/**
 * 基于 JVM 默认解析器的实现。
 */
object SystemDnsResolver : DnsResolver {

    private val DNS_ENV = Hashtable<String, String>().apply {
        // 用字符串指定工厂类，而不是 import com.sun.jndi.dns.DnsContextFactory：
        // 那个包在 JDK 9+ 的 java.naming 里不是常规导出包，JNDI 会自己从 jdk.naming.dns 模块加载。
        put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory")
        put("java.naming.provider.url", "dns:")
    }

    override fun resolveA(hostname: String): List<InetAddress> =
        try {
            InetAddress.getAllByName(hostname).toList()
        } catch (_: UnknownHostException) {
            emptyList()
        }

    /**
     * JVM 标准库没有 CNAME 查询能力：`InetAddress` 只暴露「名字 → 地址」，
     * 而 `getCanonicalHostName()` 拿到的是「地址反解」，没有 PTR 时甚至退化成 IP 字符串
     * （实测 macOS 上 `www.github.com` 返回 `20.205.243.166`）。
     * 所以这里借 JDK 自带的 JNDI DNS provider 直接查记录。
     */
    override fun resolveCname(hostname: String): String? {
        val ctx: InitialDirContext = try {
            InitialDirContext(DNS_ENV)
        } catch (_: NamingException) {
            return null
        }
        return try {
            // 补尾点强制按绝对名查：不带尾点时 JNDI 会先套用 /etc/resolv.conf 里的 search domain
            val attrs = ctx.getAttributes("${hostname.trimEnd('.')}.", arrayOf("CNAME"))
            (attrs.get("CNAME")?.get() as? String)?.trimEnd('.')
        } catch (_: NameNotFoundException) {
            null // NXDOMAIN（response code 3）
        } catch (_: NamingException) {
            null // 解析器不可达、报文异常等
        } finally {
            try {
                ctx.close()
            } catch (_: NamingException) {
                // 关不掉不影响已拿到的结果
            }
        }
    }
}

/**
 * host 是否落在预设范围之内。
 *
 * @param cidrs 形如 `10.0.0.0/8` 的 IPv4 CIDR 列表
 * @param cnameSuffixes 允许的 CNAME 目标后缀，如 `example.com` 会放行 `xx.example.com`
 */
class DnsScopeMatcher(
    cidrs: List<String>,
    cnameSuffixes: List<String>,
    private val resolver: DnsResolver = SystemDnsResolver,
) {
    private val cidrRanges: List<CidrRange> = cidrs.map(::CidrRange)

    // DNS 名字大小写不敏感，配置里写成 `ALB.Example.com.` 也该放行；
    // 空串必须剔掉，否则 `endsWith(".")` 会匹配到任何绝对名。
    private val cnameSuffixes: List<String> =
        cnameSuffixes.map { it.trim().trimEnd('.').lowercase() }.filter { it.isNotEmpty() }

    /**
     * 域名是否解析在预设范围之内（A 记录命中 CIDR，或 CNAME 链命中后缀）
     */
    fun matches(hostname: String): Boolean {
        val inCidr = resolver.resolveA(hostname).any { addr ->
            cidrRanges.any { it.contains(addr) }
        }
        if (inCidr) return true
        return matchesCnameScope(hostname)
    }

    /**
     * 沿 CNAME 链逐跳比对后缀——中间跳命中也算命中
     * （`svc.example.com → svc.cdn.net → svc.alb.example.com` 这种很常见）。
     *
     * 链长上限 [MAX_CNAME_HOPS]，并用已访问集合断环：DNS 配错造成的环不该把采样任务挂死。
     */
    private fun matchesCnameScope(hostname: String): Boolean {
        val visited = mutableSetOf(hostname.trimEnd('.').lowercase())
        var current = hostname
        repeat(MAX_CNAME_HOPS) {
            val next = resolver.resolveCname(current)?.trimEnd('.')?.lowercase() ?: return false
            if (!visited.add(next)) return false
            if (cnameSuffixes.any { next == it || next.endsWith(".$it") }) return true
            current = next
        }
        return false
    }

    private companion object {
        /** 常见递归解析器的 CNAME 链上限也在 8~16 这一档 */
        const val MAX_CNAME_HOPS = 8
    }
}

/**
 * 极简 IPv4 CIDR，不引第三方库。
 */
class CidrRange(val notation: String) {
    private val address: Int
    private val prefixLength: Int

    init {
        val parts = notation.trim().split('/')
        require(parts.size == 2) { "非法 CIDR: $notation" }
        val addrBytes = InetAddress.getByName(parts[0]).address
        require(addrBytes.size == 4) { "目前只支持 IPv4 CIDR: $notation" }
        address = bytesToInt(addrBytes)
        prefixLength = parts[1].toInt()
        require(prefixLength in 0..32) { "非法 CIDR 前缀长度: $notation" }
    }

    fun contains(other: InetAddress): Boolean {
        val otherBytes = other.address
        if (otherBytes.size != 4) return false
        val otherInt = bytesToInt(otherBytes)
        if (prefixLength == 0) return true
        val mask = -1 shl (32 - prefixLength)
        return (address and mask) == (otherInt and mask)
    }

    override fun toString(): String = notation

    private companion object {
        fun bytesToInt(bytes: ByteArray): Int =
            ((bytes[0].toInt() and 0xFF) shl 24) or
                    ((bytes[1].toInt() and 0xFF) shl 16) or
                    ((bytes[2].toInt() and 0xFF) shl 8) or
                    (bytes[3].toInt() and 0xFF)
    }
}
