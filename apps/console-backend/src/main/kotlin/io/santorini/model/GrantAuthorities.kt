package io.santorini.model

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
)
