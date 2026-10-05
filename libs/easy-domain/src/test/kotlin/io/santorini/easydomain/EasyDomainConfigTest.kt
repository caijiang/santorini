@file:Suppress("NonAsciiCharacters", "RemoveRedundantBackticks")

package io.santorini.easydomain

import java.io.File
import kotlin.test.*

class EasyDomainConfigTest {

    @Test
    fun `什么都不配则两个功能都关闭`() {
        val config = EasyDomainConfig.fromEnv { null }
        assertFalse(config.domainFeatureEnabled)
        assertFalse(config.certSyncEnabled)
    }

    @Test
    fun `namespace 加模板开启域名管理`() {
        val config = EasyDomainConfig.fromEnv { key ->
            when (key) {
                EasyDomainConfig.ENV_NAMESPACE -> "easy-domains"
                EasyDomainConfig.ENV_TEMPLATE -> "yaml-content"
                else -> null
            }
        }
        assertTrue(config.domainFeatureEnabled)
        assertFalse(config.certSyncEnabled)
        assertEquals("easy-domains", config.namespace)
        assertEquals("yaml-content", config.ingressTemplate)
    }

    @Test
    fun `阿里云配置齐全开启证书同步`() {
        val config = EasyDomainConfig.fromEnv { key ->
            when (key) {
                EasyDomainConfig.ENV_NAMESPACE -> "easy-domains"
                EasyDomainConfig.ENV_TEMPLATE -> "yaml"
                EasyDomainConfig.ENV_ALIYUN_ACCESS_KEY_ID -> "id"
                EasyDomainConfig.ENV_ALIYUN_ACCESS_KEY_SECRET -> "secret"
                EasyDomainConfig.ENV_ALIYUN_REGION -> "cn-hangzhou"
                EasyDomainConfig.ENV_ALIYUN_ALB_LISTENER_ID -> "foo"
                else -> null
            }
        }
        assertTrue(config.domainFeatureEnabled)
        assertTrue(config.certSyncEnabled)
    }

    @Test
    fun `证书同步不依赖 namespace，与域名管理各自独立`() {
        val config = EasyDomainConfig.fromEnv { key ->
            when (key) {
                EasyDomainConfig.ENV_ALIYUN_ACCESS_KEY_ID -> "id"
                EasyDomainConfig.ENV_ALIYUN_ACCESS_KEY_SECRET -> "secret"
                EasyDomainConfig.ENV_ALIYUN_REGION -> "cn-hangzhou"
                EasyDomainConfig.ENV_ALIYUN_ALB_LISTENER_ID -> "foo"
                else -> null
            }
        }
        assertFalse(config.domainFeatureEnabled)
        assertTrue(config.certSyncEnabled)
    }

    @Test
    fun `DNS 范围一个都没配时 dnsScopeConfigured 为 false`() {
        assertFalse(EasyDomainConfig.fromEnv { null }.dnsScopeConfigured)
        assertTrue(
            EasyDomainConfig.fromEnv { key ->
                if (key == EasyDomainConfig.ENV_DNS_CIDRS) "10.0.0.0/8" else null
            }.dnsScopeConfigured
        )
    }

    @Test
    fun `模板正文与模板文件互斥`() {
        val e = assertFailsWith<IllegalStateException> {
            EasyDomainConfig.fromEnv { key ->
                when (key) {
                    EasyDomainConfig.ENV_NAMESPACE -> "ns"
                    EasyDomainConfig.ENV_TEMPLATE -> "inline"
                    EasyDomainConfig.ENV_TEMPLATE_FILE -> "/tmp/x.yaml"
                    else -> null
                }
            }
        }
        assertTrue(e.message!!.contains("只能设置一个"))
    }

    @Test
    fun `模板文件路径被读取`() {
        val f = File.createTempFile("easy-domain", ".yaml").apply {
            writeText("apiVersion: v1")
            deleteOnExit()
        }
        val config = EasyDomainConfig.fromEnv { key ->
            when (key) {
                EasyDomainConfig.ENV_NAMESPACE -> "ns"
                EasyDomainConfig.ENV_TEMPLATE_FILE -> f.absolutePath
                else -> null
            }
        }
        assertEquals("apiVersion: v1", config.ingressTemplate)
    }

    @Test
    fun `CIDR 与 CNAME 后缀按逗号拆分并去空白`() {
        val config = EasyDomainConfig.fromEnv { key ->
            when (key) {
                EasyDomainConfig.ENV_DNS_CIDRS -> " 10.0.0.0/8 , 39.100.0.0/16 "
                EasyDomainConfig.ENV_DNS_CNAME_SUFFIXES -> "alb.example.com,"
                else -> null
            }
        }
        assertEquals(listOf("10.0.0.0/8", "39.100.0.0/16"), config.dnsCidrs)
        assertEquals(listOf("alb.example.com"), config.dnsCnameSuffixes)
    }
}
