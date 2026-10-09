@file:Suppress("NonAsciiCharacters", "RemoveRedundantBackticks")

package io.santorini.easydomain.aliyun

import kotlin.test.*

class AliyunNetworkTest {

    @Test
    fun `在 VPC 内且与目标同 region 才敢用 VPC 接入点`() {
        val inHangzhouVpc = AliyunNetwork("cn-hangzhou", inVpc = true)

        assertEquals(AliyunNetworkKind.VPC, inHangzhouVpc.kindFor("cn-hangzhou"))
        // VPC 接入点是地域级的，跨 region 解析不出来，只能退回公网
        assertEquals(AliyunNetworkKind.PUBLIC, inHangzhouVpc.kindFor("cn-shanghai"))
    }

    @Test
    fun `不在 VPC 内一律走公网`() {
        assertEquals(AliyunNetworkKind.PUBLIC, AliyunNetwork("cn-hangzhou", false).kindFor("cn-hangzhou"))
        assertEquals(AliyunNetworkKind.PUBLIC, AliyunNetwork.UNKNOWN.kindFor("cn-hangzhou"))
    }

    @Test
    fun `region 读不到时不用 VPC 接入点`() {
        // network-type 读到了、region-id 没读到，属于异常组合：宁可退回公网
        assertEquals(AliyunNetworkKind.PUBLIC, AliyunNetwork(null, inVpc = true).kindFor("cn-hangzhou"))
    }

    @Test
    fun `元数据里 network-type 只有等于 vpc 才算 VPC`() {
        assertEquals(AliyunNetwork("cn-hangzhou", true), aliyunNetworkOf(" cn-hangzhou\n", "VPC"))
        // 经典网络实例根本没有 network-type 这个字段
        assertFalse(aliyunNetworkOf("cn-hangzhou", null).inVpc)
        assertFalse(aliyunNetworkOf("cn-hangzhou", "classic").inVpc)
        assertNull(aliyunNetworkOf("  ", "vpc").regionId)
    }

    @Test
    fun `显式配置 auto 等于不配`() {
        assertNull(AliyunNetworkKind.of(null))
        assertNull(AliyunNetworkKind.of("  "))
        assertNull(AliyunNetworkKind.of("auto"))
        assertEquals(AliyunNetworkKind.VPC, AliyunNetworkKind.of(" vpc "))
        assertEquals(AliyunNetworkKind.PUBLIC, AliyunNetworkKind.of("PUBLIC"))
    }

    @Test
    fun `显式配置写错就抛出去，不静默跑错接入点`() {
        val e = assertFailsWith<IllegalStateException> { AliyunNetworkKind.of("vpcc") }
        assertTrue(e.message!!.contains("vpcc"))
    }

    @Test
    fun `CAS 的 VPC 接入点按官方命名规则拼`() {
        assertEquals("cas-vpc.cn-hangzhou.aliyuncs.com", casVpcEndpoint("cn-hangzhou"))
        assertEquals("cas-vpc.ap-southeast-1.aliyuncs.com", casVpcEndpoint("ap-southeast-1"))
    }
}
