package io.santorini.console.model

import kotlinx.serialization.Serializable

/**
 * 授权的系统权限体系
 * @author CJ
 */
@Serializable
data class GrantAuthorities(
    val root: Boolean,
    /**
     * 允许查看用户
     */
    val users: Boolean,
    /**
     * 允许查看以及分配自己所可见的环境
     */
    val envs: Boolean,
    /**
     * 允许查看服务角色
     */
    val roles: Boolean,
    /**
     * 允许分配权限
     */
    val assigns: Boolean,
) {
    fun toArray(): Array<String> {
        val buf = mutableListOf("ROLE_USER")
        if (users) {
            buf.add("ROLE_USERS")
        }
        if (envs) {
            buf.add("ROLE_ENVS")
        }
        if (roles) {
            buf.add("ROLE_ROLES")
        }
        if (assigns) {
            buf.add("ROLE_ASSIGN")
        }
        if (root) {
            buf.add("ROLE_ROOT")
            buf.add("ROLE_MANAGER")
        }
        return buf.toTypedArray()
    }
}
