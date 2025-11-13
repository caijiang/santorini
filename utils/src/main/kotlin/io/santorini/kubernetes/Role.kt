package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder
import io.fabric8.kubernetes.api.model.rbac.Role
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder
import io.fabric8.kubernetes.client.KubernetesClient

fun KubernetesClient.findOrCreateServiceAccountAndAssignRoles(
    root: HasMetadata,
    labels: Map<String, String>,
    vararg roles: Role
): ServiceAccount {

    val instance = root.metadata.labels["app.kubernetes.io/instance"] ?: throw Exception("顶级元素不包含实例信息")

    val current = serviceAccounts().inNamespace(namespace)
        .withLabels(labels)
        .withLabel("app.kubernetes.io/instance", instance)
        .list()
        .items
        .firstOrNull()

    if (current != null) {
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

    RoleBindingBuilder()
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
        .let {
            var c = it
            for (role in roles) {
                c = c.addNewSubject()
                    .withApiGroup("rbac.authorization.k8s.io")
                    .withKind("Role")
                    .withNamespace(role.metadata.namespace)
                    .withName(role.metadata.name)
                    .endSubject()
//                c = c.addNewSubject("rbac.authorization.k8s.io", "Role", role.metadata.name, role.metadata.namespace)
            }
            c
        }
        .build()
        .let {
            resource(it).create()
        }

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