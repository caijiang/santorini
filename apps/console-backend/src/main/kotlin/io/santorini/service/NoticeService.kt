package io.santorini.service

import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice
import io.santorini.InSiteUserData
import io.santorini.console.schema.DeploymentDeployData
import io.santorini.console.schema.DeploymentResource

/**
 * 所有方法都应该立刻完成，并且不要抛出异常
 * @author CJ
 */
interface NoticeService {
    /**
     * 发送服务不稳定的推送
     */
    fun serviceInstanceUnstable(address: String, old: EndpointSlice, new: EndpointSlice)

    /**
     * 发送自动伸缩通知
     */
    fun autoScalingHappen(
        oldDesiredReplicas: Int,
        newDesiredReplicas: Int,
        oldObj: HorizontalPodAutoscaler,
        newObj: HorizontalPodAutoscaler
    )

    /**
     * 新的部署通知
     */
    fun newDeployment(userData: InSiteUserData, data: DeploymentDeployData, deploy: DeploymentResource.Deploy)

    /**
     * 服务被调整
     */
    fun serviceMetaDataUpdated(userData: InSiteUserData, id: String)
}