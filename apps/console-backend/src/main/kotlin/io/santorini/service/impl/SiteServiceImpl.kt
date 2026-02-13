package io.santorini.service.impl

import io.santorini.service.SiteService

/**
 * @author CJ
 */
class SiteServiceImpl(
    override val appName: String = System.getenv("APPNAME") ?: "Santorini",
    override val siteHome: String = System.getenv("FEISHU_CALLBACK_URL")?.removeSuffix("/callbackFeishu")
        ?: "https://op-k8s.domain"
) : SiteService