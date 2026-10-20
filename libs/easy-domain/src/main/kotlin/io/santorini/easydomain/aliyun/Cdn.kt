package io.santorini.easydomain.aliyun

import com.aliyun.sdk.service.cdn20180510.AsyncClient
import com.aliyun.sdk.service.cdn20180510.models.DescribeDomainCertificateInfoRequest
import com.aliyun.sdk.service.cdn20180510.models.DescribeUserDomainsRequest
import com.aliyun.sdk.service.cdn20180510.models.DescribeUserDomainsResponseBody
import com.aliyun.sdk.service.cdn20180510.models.SetCdnDomainSSLCertificateRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.future.await

private val logger = KotlinLogging.logger {}

suspend fun AsyncClient.describeAllUserDomains(builder: DescribeUserDomainsRequest.Builder = DescribeUserDomainsRequest.builder())
        : List<DescribeUserDomainsResponseBody.PageData> {
    var number = 1
    val list = mutableListOf<DescribeUserDomainsResponseBody.PageData>()
    while (true) {
        val result = describeUserDomains(builder.pageNumber(number++).build()).await()
            .body

        if (result.domains.pageData.isNullOrEmpty()) {
            return list
        }

        list.addAll(result.domains.pageData)

        if (result.domains.pageData.size < result.pageSize) {
            return list
        }
    }
}

suspend fun AsyncClient.setCdnDomainSSLCertificateIfAbsent(domain: String, region: String, certId: Long) {
    val current = describeDomainCertificateInfo(
        DescribeDomainCertificateInfoRequest.builder()
            .domainName(domain)
            .build()
    ).await().body.certInfos.certInfo

    if (current.any {
            it.certId == certId.toString()
        }) {
        logger.debug { "域名 $domain 已存在证书 $certId" }
        return
    }

    val result = setCdnDomainSSLCertificate(
        SetCdnDomainSSLCertificateRequest.builder()
            .domainName(domain)
            .certId(certId)
            .certType("cas")
            .SSLProtocol("on")
            .certRegion(region)
            .build()
    ).await().body.requestId

    logger.info {
        "CDN分配证书结果:${result}"
    }
}