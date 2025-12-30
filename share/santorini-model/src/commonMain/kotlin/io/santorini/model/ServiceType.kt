package io.santorini.model

import kotlin.js.JsExport

/**
 *
 * @author CJ
 */
@Suppress("unused")
@JsExport
enum class ServiceType {
    JVM,

    /**
     * 前端服务
     */
    Nginx,

    /**
     * 没有具体的业务逻辑
     */
    Other,
}