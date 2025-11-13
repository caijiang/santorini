package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.santorini.OAuthPlatformUserData
import io.santorini.OAuthPlatformUserDataAuditResult

/**
 * 寻找用户，找不到就新增
 */
fun KubernetesClient.findOrCreateServiceAccount(
    platformUserData: OAuthPlatformUserData,
    result: OAuthPlatformUserDataAuditResult,
): ServiceAccount {
    val root = currentPod().rootOwner(this)
    val userRole = findRole(root, OAuthPlatformUserDataAuditResult.User.name.lowercase())
    val role = findRole(root, result.name.lowercase())
    val labels = mapOf(
        "santorini.io/auth-platform" to platformUserData.platform.name,
        "santorini.io/auth-pk" to platformUserData.stablePk
    )
    if (result == OAuthPlatformUserDataAuditResult.Manager) {
        return findOrCreateServiceAccountAndAssignRoles(
            root,
            labels,
            role,
            userRole
        )
    }
    return findOrCreateServiceAccountAndAssignRoles(
        root,
        labels,
        role
    )

}