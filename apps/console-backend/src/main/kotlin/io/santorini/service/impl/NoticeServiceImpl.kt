package io.santorini.service.impl

import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice
import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.InSiteUserData
import io.santorini.console.schema.*
import io.santorini.service.AsyncTaskService
import io.santorini.service.FeishuService
import io.santorini.service.NoticeService
import io.santorini.service.SiteService
import io.santorini.service.impl.feishu.FeishuParagraph
import io.santorini.service.impl.feishu.FeishuPost
import io.santorini.service.impl.feishu.FeishuTag
import io.santorini.service.impl.feishu.FeishuTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @author CJ
 */
class NoticeServiceImpl(
    private val asyncTaskService: AsyncTaskService,
    private val userCareServiceMetaService: UserCareServiceMetaService,
    private val serviceMetaService: ServiceMetaService,
    private val envService: EnvService,
    private val feishuService: FeishuService,
    private val siteService: SiteService,
) : NoticeService {
    private val logger = KotlinLogging.logger {}
    override fun serviceInstanceUnstable(address: String, old: EndpointSlice, new: EndpointSlice) {
        asyncTaskService.submit {
            val namespace = old.metadata.namespace
            val serviceId = old.metadata.labels["kubernetes.io/service-name"]
            if (namespace == null || serviceId == null) {
                logger.warn {
                    " serviceInstanceUnstable 没有合法信息的资源: new:${new}"
                }
            } else {
                userCareServiceMetaService.listNoticeTarget(namespace, serviceId).feishuTasksWithoutNames {
                    // 获取服务信息
                    val service = serviceMetaService.readServiceMetaData(serviceId) ?: return@feishuTasksWithoutNames
                    val env = envService.read(listOf(namespace)).firstOrNull()
                    val post = FeishuPost(
                        "${siteService.appName}正在汇报服务实例异常警告",
                        listOf(
                            FeishuParagraph(
                                namespace.toTags(env) +
                                        listOf(FeishuTags.text("的")) +
                                        service.toTagsWithoutLink() +
                                        listOf(
                                            FeishuTags.text("一台实例:"), FeishuTags.text(
                                                address, listOf(
                                                    "bold", "underline"
                                                )
                                            ), FeishuTags.text("出现服务异常，请及时关注")
                                        )
                            ),
                            FeishuParagraph(
                                listOf(
                                    FeishuTags.link("${siteService.siteHome}/envFor/$namespace", "查看详情")
                                )
                            )
                        )
                    )
                    it.forEach {
                        asyncTaskService.submit {
                            withContext(Dispatchers.IO) {
                                feishuService.sendSingleMessage(it, post)
                            }
                        }
                    }
                }
            }

        }
    }

    override fun autoScalingHappen(
        oldDesiredReplicas: Int,
        newDesiredReplicas: Int,
        oldObj: HorizontalPodAutoscaler,
        newObj: HorizontalPodAutoscaler
    ) {
        logger.warn {
            "autoScalingHappen ${oldDesiredReplicas},${newDesiredReplicas},${oldObj},${newObj}"
        }
    }

    override fun newDeployment(userData: InSiteUserData, data: DeploymentDeployData) {
        logger.warn {
            "newDeployment ${userData},${data}"
        }
    }

}

private fun String.toTags(env: EnvData?): List<FeishuTag> {
    if (env == null) {
        return listOf(FeishuTags.text("${this}环境"))
    }
    if (env.production) {
        return listOf(
            FeishuTags.text(env.name + "(" + env.id + ")生产环境", listOf("bold")),
        )
    }
    return listOf(
        FeishuTags.text(env.name + "(" + env.id + ")环境"),
    )
}

private fun ServiceMetaData.toTagsWithoutLink(): List<FeishuTag> {
    return listOf(
        FeishuTags.text("${name}(${id})")
    )
}

private suspend fun List<NoticeTargetUser>.feishuTasksWithoutNames(function: suspend (Set<String>) -> Unit) {
    val fs = filter { it.feishuOpenId != null }
        .map { it.feishuOpenId!! }
        .toSet()

    if (fs.isNotEmpty())
        function(fs)
}
