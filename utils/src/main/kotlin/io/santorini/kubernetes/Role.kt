package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder
import io.fabric8.kubernetes.api.model.rbac.ClusterRole
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder
import io.fabric8.kubernetes.api.model.rbac.Role
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder
import io.fabric8.kubernetes.client.KubernetesClient

fun KubernetesClient.findOrCreateServiceAccountAndAssignRoles(
    root: HasMetadata,
    labels: Map<String, String>,
    roles: List<Role>,
    clusterRoles: List<ClusterRole>
): ServiceAccount {

    val instance = root.metadata.labels["app.kubernetes.io/instance"] ?: throw Exception("顶级元素不包含实例信息")

    val current = serviceAccounts().inNamespace(namespace)
        .withLabels(labels)
        .withLabel("app.kubernetes.io/instance", instance)
        .list()
        .items
        .firstOrNull()

    if (current != null) {
        // TODO 用户可能有了，权限没有分配呢？
        return current
    }

    val create = ServiceAccountBuilder()
        .withNewMetadata()
        .addToLabels(root.inheritLabels())
        .addToLabels(labels)
        .withGenerateName("user-account")
        .withNamespace(namespace)
        .endMetadata()
        .build().let {
            resource(it).create()
        }

    val clusterRoleBindingBuilder = ClusterRoleBindingBuilder()
        .withNewMetadata()
        .withNamespace(namespace)
        .addToLabels(root.inheritLabels())
        .addToLabels(labels)
        .withGenerateName("user-account")
        .endMetadata()
        .addNewSubject()
        .withKind("ServiceAccount")
        .withName(create.metadata.name)
        .withNamespace(namespace)
        .endSubject()
    clusterRoles.map {
        clusterRoleBindingBuilder.withNewRoleRef("rbac.authorization.k8s.io", "ClusterRole", it.metadata.name)
            .build()
    }.forEach { binding -> resource(binding).create() }

    val roleBindingBuilder = RoleBindingBuilder()
        .withNewMetadata()
        .withNamespace(namespace)
        .addToLabels(root.inheritLabels())
        .addToLabels(labels)
        .withGenerateName("user-account")
        .endMetadata()
        .addNewSubject()
        .withKind("ServiceAccount")
        .withName(create.metadata.name)
        .withNamespace(namespace)
        .endSubject()
    roles.map {
        roleBindingBuilder.withNewRoleRef("rbac.authorization.k8s.io", "Role", it.metadata.name)
            .build()
    }.forEach { binding -> resource(binding).create() }

    return create
}


/**
 * @param root 肯定包含管理 meta的元素
 * @param roleName 角色名称
 * @return 寻找适合的角色
 */
fun KubernetesClient.findRole(root: HasMetadata, roleName: String): Role {
    val instance = root.metadata.labels["app.kubernetes.io/instance"] ?: throw Exception("顶级元素不包含实例信息")
    return rbac().roles()
        .inNamespace(namespace)
        .withLabel("santorini.io/role", roleName)
        .withLabel("app.kubernetes.io/instance", instance)
        .list()
        .items
        .firstOrNull() ?: throw Exception("系统没有提前制备 santorini.io/role = $roleName 的role")
}

/**
 * @param root 肯定包含管理 meta的元素
 * @param roleName 角色名称
 * @return 寻找适合的角色
 */
fun KubernetesClient.findClusterRole(root: HasMetadata, roleName: String): ClusterRole {
    val instance = root.metadata.labels["app.kubernetes.io/instance"] ?: throw Exception("顶级元素不包含实例信息")
    return rbac().clusterRoles()
        .withLabel("santorini.io/role", roleName)
        .withLabel("app.kubernetes.io/instance", instance)
        .list()
        .items
        .firstOrNull() ?: throw Exception("系统没有提前制备 santorini.io/role = $roleName 的role")
}