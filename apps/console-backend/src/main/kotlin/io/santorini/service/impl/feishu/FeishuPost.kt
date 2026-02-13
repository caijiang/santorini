package io.santorini.service.impl.feishu

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper


/**
 * https://open.feishu.cn/document/server-docs/im-v1/message-content-description/create_json#aaab037a
 * 飞书富文本
 * @author CJ
 */
data class FeishuPost(
    val title: String,
    val paragraphs: List<FeishuParagraph>
) {
    fun asJacksonRoot(): JsonNode {
        // zh_cn only
        return jacksonObjectMapper().createObjectNode()
            .apply {
                putObject("zh_cn").apply {
                    put("title", title)
                    putArray("content").apply {
                        paragraphs.forEach {
                            it.writeIntoJackson(addArray())
                        }
                    }
                }
            }
    }
}

data class FeishuParagraph(
    val tags: List<FeishuTag>,
) {
    fun writeIntoJackson(array: ArrayNode) {
        tags.forEach {
            it.writeIntoJackson(array.addObject())
        }
    }
}

interface FeishuTag {
    fun writeIntoJackson(objectNode: ObjectNode)
}

object FeishuTags {
    fun link(link: String, text: String = link, style: List<String>? = null): FeishuTag {
        return object : FeishuTag {
            override fun writeIntoJackson(objectNode: ObjectNode) {
                objectNode.put("tag", "a")
                objectNode.put("href", link)
                objectNode.put("text", text)
                style?.let {
                    objectNode.putArray("style").apply {
                        it.forEach {
                            add(it)
                        }
                    }
                }
            }
        }
    }

    fun text(text: String, style: List<String>? = null): FeishuTag {
        return object : FeishuTag {
            override fun writeIntoJackson(objectNode: ObjectNode) {
                objectNode.put("tag", "text")
                objectNode.put("text", text)
                style?.let {
                    objectNode.putArray("style").apply {
                        it.forEach {
                            add(it)
                        }
                    }
                }
            }
        }
    }
}
