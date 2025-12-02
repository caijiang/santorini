package io.santorini.model

import kotlinx.serialization.Serializable

/**
 * 资源需求
 * @author CJ
 */
@Serializable
data class ResourceRequirement(
    val type: ResourceType,
    val name: String? = null,

    ) {
    override fun toString(): String {
        return type.name + "-" + (name ?: "")
    }
}

object ResourceRequirementTools {

    fun rrFromString(rr: String): ResourceRequirement {
        val s1 = rr.split("-")
        if (s1.size <= 1) {
            throw IllegalArgumentException("Invalid resource requirement: $rr")
        }
        if (s1.size == 2) {
            return ResourceRequirement(ResourceType.valueOf(s1[0]), s1[1].let { if (it == "") null else it })
        }
        val name = s1.last().let { if (it == "") null else it }

        return ResourceRequirement(ResourceType.valueOf(s1.dropLast(1).joinToString("-")), name)
    }
}