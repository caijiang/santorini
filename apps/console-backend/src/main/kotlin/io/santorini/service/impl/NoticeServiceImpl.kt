package io.santorini.service.impl

import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricSpec
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricValueStatus
import io.fabric8.kubernetes.api.model.autoscaling.v2.ResourceMetricSource
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice
import io.github.oshai.kotlinlogging.KotlinLogging
import io.santorini.InSiteUserData
import io.santorini.console.schema.*
import io.santorini.service.AsyncTaskService
import io.santorini.service.FeishuService
import io.santorini.service.NoticeService
import io.santorini.service.SiteService
import io.santorini.service.impl.feishu.*
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

    /**
     * 服务单元关注回调
     * @param feishuBlock 关注的飞书用户
     */
    private suspend fun workWithServiceMetaFocus(
        namespace: String?,
        serviceId: String?,
        feishuBlock: suspend Set<FeishuUser>.(service: ServiceMetaData, env: EnvData?) -> Unit
    ) {
        if (namespace == null || serviceId == null) {
            logger.warn {
                " workWithFeishu 没有合法信息的资源: namespace:${namespace}, serviceId:${serviceId}"
            }
        } else {
            userCareServiceMetaService.listNoticeTarget(namespace, serviceId).feishuTasksWithoutNames {
                // 获取服务信息
                logger.debug { "listNoticeTarget, result: $it" }
                val service = serviceMetaService.readServiceMetaData(serviceId) ?: return@feishuTasksWithoutNames
                val env = envService.read(listOf(namespace)).firstOrNull()
                it.feishuBlock(service, env)
            }
        }
    }

    override fun serviceInstanceUnstable(address: String, old: EndpointSlice, new: EndpointSlice) {
        asyncTaskService.submit {
            val namespace = old.metadata.namespace
            val serviceId = old.metadata.labels["kubernetes.io/service-name"]
            logger.debug { "start serviceInstanceUnstable $address, $serviceId, $namespace" }
            workWithServiceMetaFocus(namespace, serviceId) { service, env ->
                // 获取服务信息
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
                this.forEach {
                    asyncTaskService.submit {
                        withContext(Dispatchers.IO) {
                            feishuService.sendSingleMessage(it.feishuOpenId, post)
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
        asyncTaskService.submit {
            val namespace = oldObj.metadata.namespace
            // scaleTargetRef=CrossVersionObjectReference(apiVersion=apps/v1, kind=Deployment, name=demo-service-137, additionalProperties={}),
            val serviceId = oldObj.spec?.scaleTargetRef?.name
            logger.debug { "start autoScalingHappen $oldDesiredReplicas,$newDesiredReplicas, $serviceId, $namespace" }
            workWithServiceMetaFocus(namespace, serviceId) { service, env ->
                val metricSpec = oldObj.spec?.metrics?.firstOrNull {
                    it.type == "Resource"
                }

                val post = FeishuPost(
                    "${siteService.appName}正在汇报服务自动伸缩警告",
                    listOf(
                        FeishuParagraph(
                            namespace.toTags(env) +
                                    listOf(FeishuTags.text("的")) +
                                    service.toTagsWithoutLink() +
                                    listOfNotNull(
                                        metricSpec?.resource?.toHumanReadString()?.let {
                                            FeishuTags.text("因为其自动伸缩设置[$it]")
                                        }
                                    ) +
                                    listOfNotNull(
                                        newObj.status?.currentMetrics?.firstOrNull {
                                            it.type == metricSpec?.type
                                        }?.resource?.current?.toHumanReadString(metricSpec)?.let {
                                            FeishuTags.text("当前$it，")
                                        }
                                    ) +
                                    listOf(
                                        FeishuTags.text("集群规模正在从"),
                                        FeishuTags.text(
                                            oldDesiredReplicas.toString(), listOf(
                                                "bold", "underline"
                                            )
                                        ),
                                        FeishuTags.text("伸缩到"),
                                        FeishuTags.text(
                                            newDesiredReplicas.toString(), listOf(
                                                "bold", "underline"
                                            )
                                        ),
                                    )
                        ),
                        FeishuParagraph(
                            listOf(
                                FeishuTags.link("${siteService.siteHome}/envFor/$namespace", "查看详情")
                            )
                        )
                    )
                )
                this.forEach {
                    asyncTaskService.submit {
                        withContext(Dispatchers.IO) {
                            feishuService.sendSingleMessage(it.feishuOpenId, post)
                        }
                    }
                }
            }
        }
    }

    override fun newDeployment(
        userData: InSiteUserData,
        data: DeploymentDeployData,
        deploy: DeploymentResource.Deploy
    ) {
        logger.debug {
            "newDeployment ${userData},${data}"
        }
        asyncTaskService.submit {
            workWithServiceMetaFocus(deploy.envId, deploy.serverId) { service, env ->
                val post = FeishuPost(
                    "${siteService.appName}正在汇报服务部署信息",
                    listOf(
                        FeishuParagraph(
                            listOf(FeishuTags.text(userData.name, listOf("bold")), FeishuTags.text("正在")) +
                                    deploy.envId.toTags(env) +
                                    listOf(FeishuTags.text("上部署")) +
                                    service.toTagsWithoutLink() +
                                    listOfNotNull(
                                        FeishuTags.text("新版本:"),
                                        FeishuTags.text(
                                            data.imageUrl, listOf(
                                                "bold", "underline"
                                            )
                                        )
                                    )
                        ),
                        FeishuParagraph(
                            listOf(
                                FeishuTags.link(
                                    "${siteService.siteHome}/envFor/${deploy.envId}/services/${service.id}/history",
                                    "查看详情"
                                )
                            )
                        )
                    )
                )

                this.filter {
                    it.user.userId != userData.id
                }.forEach {
                    asyncTaskService.submit {
                        withContext(Dispatchers.IO) {
                            feishuService.sendSingleMessage(it.feishuOpenId, post)
                        }
                    }
                }
            }
        }
    }

}

private fun MetricValueStatus.toHumanReadString(metricSpec: MetricSpec?): String {
    val target = metricSpec?.resource?.target
    if ("cpu".equals(metricSpec?.resource?.name, true)) {
        if ("Utilization".equals(target?.type, ignoreCase = true)) {
            return "平均CPU使用率${averageUtilization}%"
        }
        if ("Value".equals(target?.type, ignoreCase = true)) {
            return "总CPU用量${Quantity.getAmountInBytes(value)}核"
        }
        if ("AverageValue".equals(target?.type, ignoreCase = true)) {
            return "平均CPU用量${Quantity.getAmountInBytes(averageValue)}核"
        }
        throw IllegalArgumentException("并不支持:${target?.type}")
    }
    if ("memory".equals(metricSpec?.resource?.name, ignoreCase = true)) {
        if ("Utilization".equals(target?.type, ignoreCase = true)) {
            return "平均内存使用率${averageUtilization}%"
        }
        if ("Value".equals(target?.type, ignoreCase = true)) {
            return "总内存用量${Quantity.getAmountInBytes(value) / (1024.toBigDecimal() * 1024.toBigDecimal())}M"
        }
        if ("AverageValue".equals(target?.type, ignoreCase = true)) {
            return "平均内存用量${Quantity.getAmountInBytes(averageValue) / (1024.toBigDecimal() * 1024.toBigDecimal())}M"
        }
        throw IllegalArgumentException("并不支持:${target?.type}")
    }
    throw IllegalArgumentException("并不支持:${metricSpec?.resource?.name}")
}

private fun ResourceMetricSource?.toHumanReadString(): String? {
    if (this == null) {
        return null
    }
    if ("cpu".equals(name, ignoreCase = true)) {
        if ("Utilization".equals(target.type, ignoreCase = true)) {
            return "平均CPU使用率${target.averageUtilization}%"
        }
        if ("Value".equals(target.type, ignoreCase = true)) {
            return "总CPU用量${Quantity.getAmountInBytes(target.value)}核"
        }
        if ("AverageValue".equals(target.type, ignoreCase = true)) {
            return "平均CPU用量${Quantity.getAmountInBytes(target.averageValue)}核"
        }
        throw IllegalArgumentException("并不支持:${target.type}")
    }
    if ("memory".equals(name, ignoreCase = true)) {
        if ("Utilization".equals(target.type, ignoreCase = true)) {
            return "平均内存使用率${target.averageUtilization}%"
        }
        if ("Value".equals(target.type, ignoreCase = true)) {
            return "总内存用量${Quantity.getAmountInBytes(target.value) / (1024.toBigDecimal() * 1024.toBigDecimal())}M"
        }
        if ("AverageValue".equals(target.type, ignoreCase = true)) {
            return "平均内存用量${Quantity.getAmountInBytes(target.averageValue) / (1024.toBigDecimal() * 1024.toBigDecimal())}M"
        }
        throw IllegalArgumentException("并不支持:${target.type}")
    }
    throw IllegalArgumentException("并不支持:${name}")
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

private suspend fun List<NoticeTargetUser>.feishuTasksWithoutNames(function: suspend (Set<FeishuUser>) -> Unit) {
    val fs = filter { it.feishuOpenId != null }
        .map { FeishuUser(it.feishuOpenId!!, it) }
        .toSet()

    if (fs.isNotEmpty())
        function(fs)
}
