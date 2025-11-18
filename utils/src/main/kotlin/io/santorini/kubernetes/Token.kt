package io.santorini.kubernetes

import com.fasterxml.jackson.databind.ObjectMapper
import io.fabric8.kubernetes.api.model.authentication.TokenRequest
import io.fabric8.kubernetes.client.KubernetesClient
import java.util.concurrent.CompletableFuture

fun KubernetesClient.createTokenForServiceAccount(
    serviceAccountName: String,
    namespace: String
): CompletableFuture<TokenRequest> {
    val request = httpClient
        .newHttpRequestBuilder()
        .uri(
            String.format(
                "https://%s:%s/api/v1/namespaces/%s/serviceaccounts/%s/token",
                System.getenv("KUBERNETES_SERVICE_HOST"),
                System.getenv("KUBERNETES_SERVICE_PORT"),
                namespace,
                serviceAccountName
            )
        )
        .post(
            "application/json", """{
  "apiVersion": "authentication.k8s.io/v1",
  "kind": "TokenRequest",
  "spec": {
    "audiences": ["https://kubernetes.default.svc.cluster.local"],
    "expirationSeconds": 86400
  }
}"""
        )
        .build()

    return httpClient.sendAsync(request, String::class.java).handle { t, u ->
        if (u != null) {
            throw u
        }
        if (!t.isSuccessful) {
            throw IllegalStateException("Could not create token for $serviceAccountName:${t.code()}")
        }
        ObjectMapper().readValue(t.body(), TokenRequest::class.java)
    }
}
   