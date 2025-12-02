package io.santorini.resources

import io.santorini.kubernetes.SantoriniResource
import io.santorini.model.ServiceType

/**
 * 资源分配器，最终都是整成一个 secret 让pod自行读取。
 * @author CJ
 */
interface GenerateContext {

    fun addResource(resource: SantoriniResource, slotName: String?)

    /**
     * [Map.keys]必须是`C_IDENTIFIER`
     */
    fun toEnvResult(): Map<String, String>
}

object GenerateContextHome {
    fun contextFor(type: ServiceType): GenerateContext {
        if (type == ServiceType.JVM) return JvmGenerateContext()
        throw IllegalArgumentException("Unsupported type of service:$type")
    }
}