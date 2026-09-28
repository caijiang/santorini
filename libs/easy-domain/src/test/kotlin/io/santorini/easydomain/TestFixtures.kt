package io.santorini.easydomain

import io.santorini.kubernetes.model.HostData

/**
 * 测试共用的 Ingress 模板与样例数据
 */
object TestFixtures {
    val TEMPLATE_YAML = """
        apiVersion: networking.k8s.io/v1
        kind: Ingress
        metadata:
          name: easy-domain
          annotations:
            cert-manager.io/cluster-issuer: letsencrypt
        spec:
          ingressClassName: nginx
          tls:
            - hosts:
                - "{{domain}}"
              secretName: "{{domain}}"
          rules:
            - host: "{{domain}}"
              http:
                paths:
                  - path: /
                    pathType: Prefix
                    backend:
                      service:
                        name: web
                        port:
                          number: 80
    """.trimIndent()

    fun hostData(hostname: String, secretName: String? = hostname.replace(".", "-")) =
        HostData(hostname, "letsencrypt", secretName).cleanShot()
}
