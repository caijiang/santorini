package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder
import io.fabric8.kubernetes.api.model.rbac.*
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

fun KubernetesClient.assignClusterRole(
    role: String,
    vararg subjects: Subject
) {
    return assignClusterRole(rbac().clusterRoles().withName(role).get(), *subjects)
}

/**
 * 集群角色在集群里是唯一的，先查找一下，目标角色的已知 Binding, 如果已经存在的话 直接修改即可
 */
private fun KubernetesClient.assignClusterRole(
    role: ClusterRole,
    vararg subjects: Subject
) {
    val instance = role.metadata.labels["app.kubernetes.io/instance"] ?: throw Exception("顶级元素不包含实例信息")
    // 目标
    val currentBinding = rbac().clusterRoleBindings().withLabels(
        mapOf("app.kubernetes.io/instance" to instance, "santorini.io/id" to role.metadata.name)
    ).list().items.firstOrNull()

    if (currentBinding == null) {
        val obj = ClusterRoleBindingBuilder()
            .withNewMetadata()
            .withGenerateName("santorini-cb")
            .addToLabels("app.kubernetes.io/instance", instance)
            .endMetadata()
            .let { builder ->
                var c = builder
                subjects.forEach {
                    c = c.addNewSubjectLike(it).endSubject()
                }
                c
            }
            .withNewRoleRef("rbac.authorization.k8s.io", "ClusterRole", role.metadata.name)
            .build()
        rbac().clusterRoleBindings().resource(obj).create()
    } else {
        rbac().clusterRoleBindings().withName(currentBinding.metadata.name)
            .edit {
                it.subjects.addAll(subjects)
                it
            }
    }
}

/**
 * @param root 肯定包含管理 meta的元素
 * @param roleName 角色名称
 * @param creator 如果不存在则进行创建
 * @return 寻找适合的角色
 * @throws IllegalStateException 目标角色不存在
 */
@Throws(IllegalStateException::class)
fun KubernetesClient.findClusterRole(
    root: HasMetadata,
    roleName: String,
    creator: (ClusterRoleFluent<ClusterRoleBuilder>.MetadataNested<ClusterRoleBuilder>.() -> ClusterRole)? = null
): ClusterRole {
    val instance = root.metadata.labels["app.kubernetes.io/instance"] ?: throw Exception("顶级元素不包含实例信息")
    return rbac().clusterRoles()
        .withLabel("santorini.io/role", roleName)
        .withLabel("app.kubernetes.io/instance", instance)
        .list()
        .items
        .firstOrNull()
        ?: creator?.let {
            val obj = it(
                ClusterRoleBuilder()
                    .withNewMetadata()
                    .addToLabels("santorini.io/role", roleName)
                    .addToLabels("app.kubernetes.io/instance", instance)
            )
            rbac().clusterRoles().resource(obj).create()
        }
        ?: throw IllegalStateException("系统没有提前制备 santorini.io/role = $roleName 的role")
}

/**
 * @return role 名字集合
 */
fun KubernetesClient.clusterRoleBindingBySubject(subject: Subject): List<String> {
    return rbac().clusterRoleBindings().list()
        .items
        .filter { binding -> binding.subjects.any { it.name == subject.name && it.namespace == subject.namespace && it.kind == subject.kind } }
        .map { it.roleRef.name }
}