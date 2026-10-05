package io.santorini.easydomain.aliyun

import com.aliyun.sdk.service.alb20200616.models.ListListenerCertificatesResponseBody
import com.aliyun.sdk.service.cas20200407.AsyncClient
import com.aliyun.sdk.service.cas20200407.models.GetUserCertificateDetailRequest
import com.aliyun.sdk.service.cas20200407.models.GetUserCertificateDetailResponseBody
import com.aliyun.sdk.service.cas20200407.models.ListUserCertificateOrderRequest
import com.aliyun.sdk.service.cas20200407.models.ListUserCertificateOrderResponseBody
import kotlinx.coroutines.future.await


fun ListListenerCertificatesResponseBody.Certificates.toUserCertificateDetailRequest(): GetUserCertificateDetailRequest {
    return GetUserCertificateDetailRequest.builder()
        .certId(certificateId.substring(0, certificateId.indexOf("-")).toLong())
        .build()
}

fun GetUserCertificateDetailResponseBody.supportDomain(domain: String): Boolean {
    if (sans.isNotEmpty()) {
        return sans.split(",").contains(domain)
    }
    return common.equals(domain)
}

suspend fun AsyncClient.listAllUserCertificateOrder(builder: ListUserCertificateOrderRequest.Builder)
        : List<ListUserCertificateOrderResponseBody.CertificateOrderList> {
    var page = 1L
    val list = mutableListOf<ListUserCertificateOrderResponseBody.CertificateOrderList>()
    while (true) {
        val request = builder
            .currentPage(page++)
            .build()

        val rs = listUserCertificateOrder(request).await().body

        if (rs.certificateOrderList.isNullOrEmpty())
            return list

        list.addAll(rs.certificateOrderList)

        if (rs.certificateOrderList.size < rs.showSize) {
            return list
        }
    }
}