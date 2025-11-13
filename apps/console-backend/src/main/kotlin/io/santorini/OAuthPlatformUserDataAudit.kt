package io.santorini

/**
 * 验证一个外部平台用户的结果
 * @author CJ
 */
interface OAuthPlatformUserDataAudit {
    fun audit(data: OAuthPlatformUserData): OAuthPlatformUserDataAuditResult?
}

enum class OAuthPlatformUserDataAuditResult {
    Manager, User
}

object EnvOAuthPlatformUserDataAudit : OAuthPlatformUserDataAudit {
    override fun audit(data: OAuthPlatformUserData): OAuthPlatformUserDataAuditResult {
        val managerName = System.getenv("MANAGER_NAME") ?: "蒋才"
        if (data.name == managerName) return OAuthPlatformUserDataAuditResult.Manager
        return OAuthPlatformUserDataAuditResult.User
    }
}