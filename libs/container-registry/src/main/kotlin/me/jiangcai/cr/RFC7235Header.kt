package me.jiangcai.cr

import java.util.regex.Pattern

/**
 * @author CJ
 */
data class RFC7235Header(
    val type: String, // Bearer
    val realm: String,
//    val service: String,
//    val scope: String?,
    val properties: Map<String, String>,
) {
    companion object {
        private val x: Pattern = Pattern.compile("(\\w+)=\"(.+)\"")

        /**
         * @param header An RFC7235 compliant authentication challenge header.
         */
        fun ofString(header: String): RFC7235Header {
            val list = header.split(Pattern.compile("\\s+"), 2)
            val list2 = list[1].split(",")
                .map(String::trim).mapNotNull {
                    val mc = x.matcher(it)
                    if (mc.matches()) {
                        mc
                    } else
                        null
                }
                .associate {
                    it.group(1) to it.group(2)
                }
            // realm="https://ghcr.io/token"
            val realm = list2["realm"] ?: throw IllegalStateException("Unauthorized")

            return RFC7235Header(
                list[0], realm,
                list2 - "realm"
            )
        }
    }
}
