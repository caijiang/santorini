package io.santorini.easydomain.aliyun

import com.aliyun.sdk.service.alb20200616.models.ListListenerCertificatesResponseBody
import com.aliyun.sdk.service.cas20200407.AsyncClient
import com.aliyun.sdk.service.cas20200407.models.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.easydomain.certificateSha1Fingerprint
import kotlinx.coroutines.future.await

private val logger = KotlinLogging.logger {}

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

suspend fun AsyncClient.uploadUserCertificateIfNotExist(name: String, certPem: String, privateKeyPem: String): Long {
    val fingerprint = certificateSha1Fingerprint(certPem)

    val casCurrent = listAllUserCertificateOrder(
        ListUserCertificateOrderRequest.builder()
            .orderType("CERT")
    )

    return casCurrent.find {
        it.fingerprint.equals(fingerprint, ignoreCase = true)
    }?.certificateId ?: run {
        logger.debug { "目前没有适配的证书，上传之" }

        val result = uploadUserCertificate(
            UploadUserCertificateRequest.builder()
                .name(name)
                .cert(certPem)
                .key(privateKeyPem)
                .clientToken("upload-$name")
                .build()
        ).await().body

        logger.info {
            "cas上传证书: ${result.requestId} ${result.certId}"
        }
        result.certId
    }
}
