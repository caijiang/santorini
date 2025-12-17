package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder
import io.fabric8.kubernetes.api.model.rbac.*
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.model.ServiceRole

private val logger = KotlinLogging.logger {}

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

// role -> santorini.io/service -> %s
// role -> santorini.io/role -> (role|lc)
// Binding -> santorini.io/type -> service-role

fun KubernetesClient.removeAllServiceRolesFromNamespace(
    root: HasMetadata,
    serviceAccountName: String,
    namespace: String
) {
    val instance = root.metadata.labels["app.kubernetes.io/instance"] ?: throw Exception("顶级元素不包含实例信息")
    val all = rbac().roleBindings().inNamespace(namespace)
        .withLabel("santorini.io/type", "service-role")
        .withLabel("app.kubernetes.io/instance", instance)
        .list()
        .items

    val toDelete = all
        .filter { it -> it.subjects.any { it.name == serviceAccountName && it.kind == "ServiceAccount" && it.namespace == this.namespace } && it.subjects.size == 1 }
    if (toDelete.isEmpty()) {
        toDelete.forEach {
            rbac().roleBindings().resource(it).delete()
        }
    }

}

fun KubernetesClient.makesureRightServiceRoles(
    root: HasMetadata,
    serviceAccountName: String,
    namespace: String,
    serviceId: String,
    roles: List<ServiceRole>
) {
    val instance = root.metadata.labels["app.kubernetes.io/instance"] ?: throw Exception("顶级元素不包含实例信息")
    // 角色 -> 基本权限 -> 特殊权限
    // 有多的 移除,
    // services ,get,list ! create,delete,update
    val current = rbac().roleBindings().inNamespace(namespace)
        .withLabel("santorini.io/type", "service-role")
        .withLabel("app.kubernetes.io/instance", instance)
        .list()
        .items
        .filter { it ->
            it.subjects.any {
                it.name == serviceAccountName && it.kind == "ServiceAccount"
                        && it.namespace == this.namespace
            }
        }
    //
    val currentMore = current.associateWith {
        moreX(this, it)
    }

    val target = (roles + null).map { serviceId to it }

    currentMore.filterValues {
        // 空或者不在目标范围的
        it == null || !target.contains(it)
    }.forEach { (k, _) ->
        logger.debug {
            "RB:${k.metadata} 不应该存在,准备移除"
        }
        rbac().roleBindings().resource(k).delete()
    }

    target.filter { !currentMore.containsValue(it) }
        .forEach {
            logger.debug {
                "$it 权限缺失……正在定位 Role 以及进行绑定"
            }
            val role = findOrCreateRole(namespace, root, this, it.first, it.second)
            val b = RoleBindingBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withGenerateName("service-role")
                .addToLabels("santorini.io/type", "service-role")
                .addToLabels("app.kubernetes.io/instance", instance)
                .endMetadata()
                .withRoleRef(
                    RoleRef("rbac.authorization.k8s.io", "Role", role.metadata.name)
                )
                .withSubjects(
                    SubjectBuilder()
                        .withKind("ServiceAccount")
                        .withNamespace(this.namespace)
                        .withName(serviceAccountName)
                        .build()
                ).build()

            rbac().roleBindings().resource(b).create()
        }
}

private const val serviceRoleDataVersion = "1"
private fun findOrCreateRole(
    namespace: String,
    root: HasMetadata,
    client: KubernetesClient,
    serviceId: String,
    role: ServiceRole?
): Role {
    val instance = root.metadata.labels["app.kubernetes.io/instance"] ?: throw Exception("顶级元素不包含实例信息")

    val labels = mapOf(
        "app.kubernetes.io/instance" to instance,
        "santorini.io/service" to serviceId,
        "santorini.io/role" to (role?.name?.lowercase() ?: "basic"),
        "santorini.io/version" to serviceRoleDataVersion
    )

    val target = client.rbac().roles().inNamespace(namespace)
        .withLabels(labels)
        .list()
        .items
        .firstOrNull()
    if (target != null) {
        return target
    }
    val item = buildRole(
        RoleBuilder().withNewMetadata()
            .withGenerateName("service-role")
            .withNamespace(namespace)
            .withLabels<String, String>(labels)
            .endMetadata(), serviceId, role
    )
    return client.rbac().roles().resource(item).create()
}

private fun buildRole(builder: RoleBuilder, serviceId: String, role: ServiceRole?): Role {
    if (role == null) {
        return builder.addToRules(
            PolicyRule(listOf(""), null, listOf(serviceId), listOf("services"), listOf("list", "get")),
        ).build()
    }
    if (role == ServiceRole.Owner) {
        return builder.addToRules(
            PolicyRule(listOf(""), null, listOf(serviceId), listOf("services"), listOf("create", "update", "delete")),
        )
            .build()
    }
    TODO("还存在:$role")
}

/**
 * 分析 Binding 获取其他属于的 serviceId,role
 */
private fun moreX(client: KubernetesClient, binding: RoleBinding): Pair<String, ServiceRole?>? {
    val role = client.rbac().roles()
        .inNamespace(binding.metadata.namespace)
        .withName(binding.roleRef.name)
        .get()

    // 版本，版本替换时 role 无需删除 只要删除 binding 即可
    val service = role.metadata?.labels?.get("santorini.io/service") ?: return null
    val asRole = role.metadata?.labels?.get("santorini.io/role") ?: return null
    val version = role.metadata?.labels?.get("santorini.io/version") ?: return null
    if (version != serviceRoleDataVersion) return null
    return service to ServiceRole.entries.firstOrNull { it.name.equals(asRole, true) }
}
