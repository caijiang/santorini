package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus

/**
 * @author CJ
 */
enum class DeploymentRolloutState(val finalState: Boolean) {
    FAILED(true),
    IN_PROGRESS(false),
    COMPLETE(true),
    SUCCESS(true),
}

private fun isDeploymentFailed(status: DeploymentStatus): Boolean {
    val conditions = status.conditions ?: return false

    return conditions.any { condition ->
        (condition.type == "Progressing" && condition.reason == "ProgressDeadlineExceeded") ||
                (condition.type == "ReplicaFailure" && condition.status == "True")
    }
}

fun Deployment.evaluateDeploymentStatus(): DeploymentRolloutState {
    val deployment = this
    val status = deployment.status ?: return DeploymentRolloutState.IN_PROGRESS
    val spec = deployment.spec ?: return DeploymentRolloutState.IN_PROGRESS

    val desired = spec.replicas ?: 0

    // 1️⃣ 是否失败（优先级最高）
    if (isDeploymentFailed(status)) {
        return DeploymentRolloutState.FAILED
    }

    // 2️⃣ 是否 controller 还没处理最新 spec
    if (status.observedGeneration == null ||
        status.observedGeneration < (deployment.metadata?.generation ?: 0L)
    ) {
        return DeploymentRolloutState.IN_PROGRESS
    }

    // 3️⃣ 是否 rollout 已完成（全部 updated）
    val rolloutComplete =
        status.updatedReplicas == desired &&
                status.replicas == desired

    if (!rolloutComplete) {
        return DeploymentRolloutState.IN_PROGRESS
    }

    // 4️⃣ 是否成功（全部可用）
    val success =
        status.readyReplicas == desired &&
                status.availableReplicas == desired

    return if (success) {
        DeploymentRolloutState.SUCCESS
    } else {
        DeploymentRolloutState.COMPLETE
    }
}