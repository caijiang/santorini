package io.santorini.easydomain.aliyun

import com.aliyun.sdk.service.alb20200616.AsyncClient
import com.aliyun.sdk.service.alb20200616.models.ListListenerCertificatesRequest
import com.aliyun.sdk.service.alb20200616.models.ListListenerCertificatesResponseBody
import kotlinx.coroutines.future.await


suspend fun AsyncClient.listAllListenerCertificates(builder: ListListenerCertificatesRequest.Builder):
        List<ListListenerCertificatesResponseBody.Certificates> {
    var next: String? = null
    val list = mutableListOf<ListListenerCertificatesResponseBody.Certificates>()
    while (true) {
        val request = builder
            .nextToken(next)
            .build()
        val response = listListenerCertificates(request).await().body
        next = response.nextToken

        if (response.certificates.isNullOrEmpty()) {
            return list
        }
        list.addAll(response.certificates)
        if (list.size >= response.totalCount) {
            return list
        }
    }
}