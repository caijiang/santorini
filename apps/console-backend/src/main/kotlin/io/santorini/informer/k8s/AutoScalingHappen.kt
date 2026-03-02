package io.santorini.informer.k8s

import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.informers.ResourceEventHandler
import io.fabric8.kubernetes.client.informers.SharedIndexInformer
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 当自动伸缩发生的时候
 * @author CJ
 */
fun KubernetesClient.autoScalingHappen(
    namespace: String,
    handler: ResourceEventHandler<HorizontalPodAutoscaler>? = null,
    onDesiredChange: (
        oldDesiredReplicas: Int, newDesiredReplicas: Int, oldObj: HorizontalPodAutoscaler, newObj: HorizontalPodAutoscaler
    ) -> Unit
): SharedIndexInformer<HorizontalPodAutoscaler> {
    return this.autoscaling().v2()
        .horizontalPodAutoscalers()
        .inNamespace(namespace)
        .inform(object : ResourceEventHandler<HorizontalPodAutoscaler> {
            override fun onAdd(obj: HorizontalPodAutoscaler?) {
                handler?.onAdd(obj)
            }

            override fun onUpdate(oldObj: HorizontalPodAutoscaler?, newObj: HorizontalPodAutoscaler?) {
                handler?.onUpdate(oldObj, newObj)
                logger.trace {
                    "onUpdate $oldObj, $newObj"
                }
                val newDR = newObj?.status?.desiredReplicas ?: 0
                val oldDR = oldObj?.status?.desiredReplicas ?: 0
                val changed = newDR - oldDR
                if (changed != 0 && oldDR != 0 && newDR != 0) {
                    logger.debug {
                        "从$oldDR 变更到$newDR"
                    }
                    onDesiredChange(oldDR, newDR, oldObj!!, newObj!!)
//                            onHorizontalPodAutoscalerUpdate----->
//                            oldObj: HorizontalPodAutoscaler(apiVersion=autoscaling/v2, kind=HorizontalPodAutoscaler, metadata=ObjectMeta(annotations={}, creationTimestamp=2026-02-11T07:52:47Z, deletionGracePeriodSeconds=null, deletionTimestamp=null, finalizers=[], generateName=null, generation=null, labels={}, managedFields=[ManagedFieldsEntry(apiVersion=autoscaling/v2, fieldsType=FieldsV1, fieldsV1=FieldsV1(additionalProperties={f:spec={f:maxReplicas={}, f:metrics={}, f:minReplicas={}, f:scaleTargetRef={f:apiVersion={}, f:kind={}, f:name={}}}}), manager=santorini, operation=Update, subresource=null, time=2026-02-11T08:05:45Z, additionalProperties={}), ManagedFieldsEntry(apiVersion=autoscaling/v2, fieldsType=FieldsV1, fieldsV1=FieldsV1(additionalProperties={f:status={f:conditions={.={}, k:{"type":"AbleToScale"}={.={}, f:lastTransitionTime={}, f:message={}, f:reason={}, f:status={}, f:type={}}, k:{"type":"ScalingActive"}={.={}, f:lastTransitionTime={}, f:message={}, f:reason={}, f:status={}, f:type={}}, k:{"type":"ScalingLimited"}={.={}, f:lastTransitionTime={}, f:message={}, f:reason={}, f:status={}, f:type={}}}, f:currentMetrics={}, f:currentReplicas={}, f:desiredReplicas={}, f:lastScaleTime={}}}), manager=kube-controller-manager, operation=Update, subresource=status, time=2026-02-11T08:10:18Z, additionalProperties={})], name=demo-service-137, namespace=test-ns, ownerReferences=[], resourceVersion=31996164, selfLink=null, uid=c2c97b82-a874-4158-8c8e-8db6a0745c41, additionalProperties={}), spec=HorizontalPodAutoscalerSpec(behavior=null, maxReplicas=5, metrics=[MetricSpec(containerResource=null, external=null, object=null, pods=null, resource=ResourceMetricSource(name=cpu, target=MetricTarget(averageUtilization=71, averageValue=null, type=Utilization, value=null, additionalProperties={}), additionalProperties={}), type=Resource, additionalProperties={})], minReplicas=1, scaleTargetRef=CrossVersionObjectReference(apiVersion=apps/v1, kind=Deployment, name=demo-service-137, additionalProperties={}), additionalProperties={}), status=HorizontalPodAutoscalerStatus(conditions=[HorizontalPodAutoscalerCondition(lastTransitionTime=2026-02-11T07:53:02Z, message=recent recommendations were higher than current one, applying the highest recent recommendation, reason=ScaleDownStabilized, status=True, type=AbleToScale, additionalProperties={}), HorizontalPodAutoscalerCondition(lastTransitionTime=2026-02-11T07:53:02Z, message=the HPA was able to successfully calculate a replica count from cpu resource utilization (percentage of request), reason=ValidMetricFound, status=True, type=ScalingActive, additionalProperties={}), HorizontalPodAutoscalerCondition(lastTransitionTime=2026-02-11T07:53:32Z, message=the desired replica count is more than the maximum replica count, reason=TooManyReplicas, status=True, type=ScalingLimited, additionalProperties={})], currentMetrics=[MetricStatus(containerResource=null, external=null, object=null, pods=null, resource=ResourceMetricStatus(current=MetricValueStatus(averageUtilization=16, averageValue=16m, value=null, additionalProperties={}), name=cpu, additionalProperties={}), type=Resource, additionalProperties={})], currentReplicas=5, desiredReplicas=5, lastScaleTime=2026-02-11T07:53:17Z, observedGeneration=null, additionalProperties={}), additionalProperties={})
//                            newObj: HorizontalPodAutoscaler(apiVersion=autoscaling/v2, kind=HorizontalPodAutoscaler, metadata=ObjectMeta(annotations={}, creationTimestamp=2026-02-11T07:52:47Z, deletionGracePeriodSeconds=null, deletionTimestamp=null, finalizers=[], generateName=null, generation=null, labels={}, managedFields=[ManagedFieldsEntry(apiVersion=autoscaling/v2, fieldsType=FieldsV1, fieldsV1=FieldsV1(additionalProperties={f:spec={f:maxReplicas={}, f:metrics={}, f:minReplicas={}, f:scaleTargetRef={f:apiVersion={}, f:kind={}, f:name={}}}}), manager=santorini, operation=Update, subresource=null, time=2026-02-11T08:05:45Z, additionalProperties={}), ManagedFieldsEntry(apiVersion=autoscaling/v2, fieldsType=FieldsV1, fieldsV1=FieldsV1(additionalProperties={f:status={f:conditions={.={}, k:{"type":"AbleToScale"}={.={}, f:lastTransitionTime={}, f:message={}, f:reason={}, f:status={}, f:type={}}, k:{"type":"ScalingActive"}={.={}, f:lastTransitionTime={}, f:message={}, f:reason={}, f:status={}, f:type={}}, k:{"type":"ScalingLimited"}={.={}, f:lastTransitionTime={}, f:message={}, f:reason={}, f:status={}, f:type={}}}, f:currentMetrics={}, f:currentReplicas={}, f:desiredReplicas={}, f:lastScaleTime={}}}), manager=kube-controller-manager, operation=Update, subresource=status, time=2026-02-11T08:10:33Z, additionalProperties={})], name=demo-service-137, namespace=test-ns, ownerReferences=[], resourceVersion=31996240, selfLink=null, uid=c2c97b82-a874-4158-8c8e-8db6a0745c41, additionalProperties={}), spec=HorizontalPodAutoscalerSpec(behavior=null, maxReplicas=5, metrics=[MetricSpec(containerResource=null, external=null, object=null, pods=null, resource=ResourceMetricSource(name=cpu, target=MetricTarget(averageUtilization=71, averageValue=null, type=Utilization, value=null, additionalProperties={}), additionalProperties={}), type=Resource, additionalProperties={})], minReplicas=1, scaleTargetRef=CrossVersionObjectReference(apiVersion=apps/v1, kind=Deployment, name=demo-service-137, additionalProperties={}), additionalProperties={}), status=HorizontalPodAutoscalerStatus(conditions=[HorizontalPodAutoscalerCondition(lastTransitionTime=2026-02-11T07:53:02Z, message=the HPA controller was able to update the target scale to 4, reason=SucceededRescale, status=True, type=AbleToScale, additionalProperties={}), HorizontalPodAutoscalerCondition(lastTransitionTime=2026-02-11T07:53:02Z, message=the HPA was able to successfully calculate a replica count from cpu resource utilization (percentage of request), reason=ValidMetricFound, status=True, type=ScalingActive, additionalProperties={}), HorizontalPodAutoscalerCondition(lastTransitionTime=2026-02-11T08:10:33Z, message=the desired count is within the acceptable range, reason=DesiredWithinRange, status=False, type=ScalingLimited, additionalProperties={})], currentMetrics=[MetricStatus(containerResource=null, external=null, object=null, pods=null, resource=ResourceMetricStatus(current=MetricValueStatus(averageUtilization=16, averageValue=16m, value=null, additionalProperties={}), name=cpu, additionalProperties={}), type=Resource, additionalProperties={})], currentReplicas=5, desiredReplicas=4, lastScaleTime=2026-02-11T08:10:33Z, observedGeneration=null, additionalProperties={}), additionalProperties={})
//                            oldObj.status.currentMetrics[0].resource.current.
                    // spec,  currentMetrics
//                            从5 变更到4
                }
            }

            override fun onDelete(obj: HorizontalPodAutoscaler?, deletedFinalStateUnknown: Boolean) {
                handler?.onDelete(obj, deletedFinalStateUnknown)
            }
        })
}