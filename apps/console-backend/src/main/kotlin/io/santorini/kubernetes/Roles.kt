package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.client.KubernetesClient
import io.santorini.OAuthPlatformUserDataAuditResult

/**
 * 寻找用户，找不到就新增
 */
fun KubernetesClient.findOrCreateServiceAccount(
    platformName: String,
    stablePk: String,
    manager: Boolean,
): ServiceAccount {
    val root = currentPod().rootOwner(this)
    val userRole = findRole(root, OAuthPlatformUserDataAuditResult.User.name.lowercase())
    val userClusterRole = findClusterRole(root, OAuthPlatformUserDataAuditResult.User.name.lowercase())
    val role = findRole(root, if (manager) "manager" else "user")
    val clusterRole = findClusterRole(root, if (manager) "manager" else "user")

    val labels = mapOf(
        "santorini.io/auth-platform" to platformName,
        "santorini.io/auth-pk" to stablePk
    )
    if (manager) {
        return findOrCreateServiceAccountAndAssignRoles(
            root,
            labels,
            listOf(userRole, role),
            listOf(userClusterRole, clusterRole)
        )
    }
    return findOrCreateServiceAccountAndAssignRoles(
        root,
        labels,
        listOf(role),
        listOf(clusterRole)
    )

}