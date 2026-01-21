package io.santorini.kubernetes

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder
import io.fabric8.kubernetes.api.model.rbac.*
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable
import io.fabric8.kubernetes.client.dsl.Resource
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


/**
 * 如果只属于目标 subject 则执行删除
 */
private fun FilterWatchListDeletable<RoleBinding, RoleBindingList, Resource<RoleBinding>>.deleteIfOnlyBelongs(
    client: KubernetesClient,
    namespace: String,
    kind: String,
    name: String
) {
    val all = list().items

    val toDelete = all
        .filter { it -> it.subjects.any { it.name == name && it.kind == kind && it.namespace == namespace } && it.subjects.size == 1 }
    if (toDelete.isEmpty()) {
        toDelete.forEach {
            client.rbac().roleBindings().resource(it).delete()
        }
    }
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
    rbac().roleBindings().inNamespace(namespace)
        .withLabel("santorini.io/type", "service-role")
        .withLabel("app.kubernetes.io/instance", instance)
        .deleteIfOnlyBelongs(this, this.namespace, "ServiceAccount", serviceAccountName)

    rbac().roleBindings().inNamespace(namespace)
        .withLabel("santorini.io/type", "env-role")
        .withLabel("app.kubernetes.io/instance", instance)
        .deleteIfOnlyBelongs(this, this.namespace, "ServiceAccount", serviceAccountName)

}

fun KubernetesClient.makesureRightEnvRoles(
    root: HasMetadata,
    serviceAccountName: String,
    namespace: String
) {
    val instance = root.metadata.labels["app.kubernetes.io/instance"] ?: throw Exception("顶级元素不包含实例信息")

    val labels = mapOf(
        "app.kubernetes.io/instance" to instance,
        "santorini.io/type" to "env-role",
        "santorini.io/version" to "4"
    )

    val role = findOrCreateRoleImpl(this, namespace, labels, "env-role-") {
        it.addToRules(
            PolicyRule(
                listOf(""), null, listOf(), listOf("pods", "pods/log"), listOf("list", "get")
            ),
            PolicyRule(
                listOf("apps"), null, listOf(), listOf("replicasets"), listOf("list", "get")
            ),
            PolicyRule(
                listOf("autoscaling"), null, listOf(), listOf("horizontalpodautoscalers"), listOf("list")
            ),
            PolicyRule(
                listOf(""), null, listOf(), listOf("events"), listOf("list")
            )
        ).build()
    }

    // 寻找我的
    val current = rbac().roleBindings().inNamespace(namespace)
        .withLabel("santorini.io/type", "env-role")
        .withLabel("app.kubernetes.io/instance", instance)
        .list()
        .items
        .filter { it ->
            it.subjects.any {
                it.name == serviceAccountName && it.kind == "ServiceAccount"
                        && it.namespace == this.namespace
            }
        }
    // 删除过时的
    current.filter {
        it.roleRef.name != role.metadata.name
    }.forEach {
        rbac().roleBindings().resource(it).delete()
    }
    // 绑定现有的
    if (current.none { it.roleRef.name == role.metadata.name }) {
        val b = RoleBindingBuilder()
            .withNewMetadata()
            .withNamespace(namespace)
            .withGenerateName("env-role-")
            .addToLabels("santorini.io/type", "env-role")
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

fun KubernetesClient.makesureRightServiceRoles(
    root: HasMetadata,
    serviceAccountName: String,
    namespace: String,
    servicesRoles: Map<String, List<ServiceRole>>
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
    // 这些 Binding 分别代表什么; Pair null 表示无关, ServiceRole null 表示 基础权限
    val currentMore = current.associateWith {
        moreX(this, it)
    }

    // 目标权限组
    val target = servicesRoles.flatMap { entry ->
        (entry.value + null).map { entry.key to it }
    }

    currentMore.filterValues {
        // 空或者不在目标范围的
        it == null || !target.contains(it)
    }.forEach { (k, _) ->
        logger.warn {
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

private const val serviceRoleDataVersion = "6"
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

    return findOrCreateRoleImpl(client, namespace, labels, "service-role") {
        buildRole(it, serviceId, role)
    }
}

private fun findOrCreateRoleImpl(
    client: KubernetesClient,
    namespace: String,
    labels: Map<String, String>,
    generateName: String,
    api: (builder: RoleBuilder) -> Role
): Role {
    val target = client.rbac().roles().inNamespace(namespace)
        .withLabels(labels)
        .list()
        .items
        .firstOrNull()
    if (target != null) {
        return target
    }
    val item = api(
        RoleBuilder().withNewMetadata()
            .withGenerateName(generateName)
            .withNamespace(namespace)
            .withLabels<String, String>(labels)
            .endMetadata()
    )
    return client.rbac().roles().resource(item).create()
}

private fun buildRole(builder: RoleBuilder, serviceId: String, role: ServiceRole?): Role {
    if (role == null) {
        return builder.addToRules(
            PolicyRule(
                listOf("autoscaling"),
                null,
                listOf(serviceId),
                listOf("horizontalpodautoscalers"),
                listOf("list", "get")
            ),
            PolicyRule(listOf(""), null, listOf(serviceId), listOf("services"), listOf("list", "get")),
            PolicyRule(
                listOf("apps"),
                null,
                listOf(serviceId),
                listOf("deployments", "deployments/newreplicaset"),
                listOf("list", "get")
            ),
        ).build()
    }
    if (role == ServiceRole.Owner) {
        return builder.addToRules(
            PolicyRule(
                listOf("autoscaling"),
                null,
                listOf(serviceId),
                listOf("horizontalpodautoscalers"),
                listOf("create", "update", "delete", "patch")
            ),
            // pod 名字随机的，没有办法了，只能按照 k8s 的设计哲学 广权但自律
            PolicyRule(
                listOf(""), null, listOf(), listOf("pods/exec"), listOf("create", "get", "list")
            ),
            PolicyRule(listOf(""), null, listOf(serviceId), listOf("services"), listOf("create", "update", "delete")),
            PolicyRule(listOf(""), null, listOf(), listOf("services"), listOf("create")),
            PolicyRule(
                listOf("apps"),
                null,
                listOf(serviceId),
                listOf("deployments"),
                listOf("create", "update", "delete", "patch")
            ),
            PolicyRule(
                listOf("apps"),
                null,
                listOf(),
                listOf("deployments"),
                listOf("create")
            ),
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
