package io.santorini.model

import kotlinx.serialization.Serializable

/**
 * [规格](https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/#Probe)
 *
 * @author CJ
 */
@Serializable
data class Probe(
    val httpGet: HTTPGetAction? = null,
    /**
     * 0
     */
    val initialDelaySeconds: Int,
    /**
     * How often (in seconds) to perform the probe. Default to 10 seconds. Minimum value is 1.
     */
    val periodSeconds: Int? = null,
    /**
     * Number of seconds after which the probe times out. Defaults to 1 second. Minimum value is 1.
     */
    val timeoutSeconds: Int? = null,
    /**
     * Minimum consecutive failures for the probe to be considered failed after having succeeded. Defaults to 3. Minimum value is 1.
     */
    val failureThreshold: Int? = null,
    /**
     * Minimum consecutive successes for the probe to be considered successful after having failed. Defaults to 1. Must be 1 for liveness and startup. Minimum value is 1.
     */
    val successThreshold: Int? = null,
)
