package io.santorini.model

import kotlin.js.JsExport

/**
 * 用户可以承担的服务角色
 * @author CJ
 */
@JsExport
enum class ServiceRole(@Suppress("unused") val title: String) {
    Owner("负责人")
}