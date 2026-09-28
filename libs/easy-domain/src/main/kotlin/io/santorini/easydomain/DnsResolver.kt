package io.santorini.easydomain

import java.net.InetAddress
import java.net.UnknownHostException

/**
 * DNS 解析的可注入抽象——单测里用假实现，生产用系统解析。
 */
interface DnsResolver {
    /**
     * @return 解析到的所有地址；解析失败返回空集合
     */
    fun resolveA(hostname: String): List<InetAddress>

    /**
     * @return CNAME 目标域名；没有 CNAME（或解析失败）返回 null
     */
    fun resolveCname(hostname: String): String?
}

/**
 * 基于 JVM 默认解析器的实现。
 */
object SystemDnsResolver : DnsResolver {
    override fun resolveA(hostname: String): List<InetAddress> =
        try {
            InetAddress.getAllByName(hostname).toList()
        } catch (_: UnknownHostException) {
            emptyList()
        }

    override fun resolveCname(hostname: String): String? {
        // JVM 标准库拿不到裸 CNAME，退而求其次：让系统解析器自己追 CNAME 链，
        // 最终地址的 getCanonicalHostName 即链尾域名
        return try {
            val addr = InetAddress.getByName(hostname)
            addr.canonicalHostName.takeIf { !it.equals(hostname, ignoreCase = true) }
        } catch (_: UnknownHostException) {
            null
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
    private val cnameSuffixes: List<String>,
    private val resolver: DnsResolver = SystemDnsResolver,
) {
    private val cidrRanges: List<CidrRange> = cidrs.map(::CidrRange)

    /**
     * 域名是否解析在预设范围之内（A 记录命中 CIDR，或 CNAME 链命中后缀）
     */
    fun matches(hostname: String): Boolean {
        val inCidr = resolver.resolveA(hostname).any { addr ->
            cidrRanges.any { it.contains(addr) }
        }
        if (inCidr) return true

        val cname = resolver.resolveCname(hostname) ?: return false
        return cnameSuffixes.any { suffix ->
            cname.equals(suffix, ignoreCase = true) || cname.endsWith(".$suffix", ignoreCase = true)
        }
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
