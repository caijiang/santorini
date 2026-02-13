package io.santorini.informer.k8s

import io.fabric8.kubernetes.api.model.discovery.v1.EndpointConditions
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.informers.ResourceEventHandler
import io.fabric8.kubernetes.client.informers.SharedIndexInformer
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 当服务不稳定时发生回调
 * @author CJ
 */
fun KubernetesClient.serviceInstanceUnstable(
    namespace: String,
    callback: (address: String, oldObj: EndpointSlice, newObj: EndpointSlice) -> Unit
): SharedIndexInformer<EndpointSlice> {
    return this.discovery().v1()
        .endpointSlices()
        .inNamespace(namespace)
        .inform(object : ResourceEventHandler<EndpointSlice> {
            override fun onAdd(obj: EndpointSlice?) {
            }

            override fun onUpdate(oldObj: EndpointSlice?, newObj: EndpointSlice?) {
                logger.trace {
                    "onUpdate $oldObj, $newObj"
                }
//                        println("onUpdate----->\noldObj: $oldObj\r\nnewObj: $newObj")
//                        onUpdate----->
//                        oldObj: EndpointSlice(addressType=IPv4, apiVersion=discovery.k8s.io/v1, endpoints=[Endpoint(addresses=[10.244.0.7], conditions=EndpointConditions(ready=false, serving=false, terminating=false, additionalProperties={}), deprecatedTopology={}, hints=null, hostname=null, nodeName=sit001, targetRef=ObjectReference(apiVersion=null, fieldPath=null, kind=Pod, name=demo-service-137-74cb7ccb66-hg4kt, namespace=test-ns, resourceVersion=null, uid=9f22f3bd-1af2-4824-bab3-40b555dd1f9f, additionalProperties={}), zone=cn-hangzhou-g, additionalProperties={})], kind=EndpointSlice, metadata=ObjectMeta(annotations={endpoints.kubernetes.io/last-change-trigger-time=2026-02-11T06:46:22Z}, creationTimestamp=2026-01-02T08:11:41Z, deletionGracePeriodSeconds=null, deletionTimestamp=null, finalizers=[], generateName=demo-service-137-, generation=9964, labels={endpointslice.kubernetes.io/managed-by=endpointslice-controller.k8s.io, kubernetes.io/service-name=demo-service-137}, managedFields=[ManagedFieldsEntry(apiVersion=discovery.k8s.io/v1, fieldsType=FieldsV1, fieldsV1=FieldsV1(additionalProperties={f:addressType={}, f:endpoints={}, f:metadata={f:annotations={.={}, f:endpoints.kubernetes.io/last-change-trigger-time={}}, f:generateName={}, f:labels={.={}, f:endpointslice.kubernetes.io/managed-by={}, f:kubernetes.io/service-name={}}, f:ownerReferences={.={}, k:{"uid":"a217ed19-750b-4bbd-964b-136ea9e8f45b"}={}}}, f:ports={}}), manager=kube-controller-manager, operation=Update, subresource=null, time=2026-02-11T06:46:22Z, additionalProperties={})], name=demo-service-137-8c9mx, namespace=test-ns, ownerReferences=[OwnerReference(apiVersion=v1, blockOwnerDeletion=true, controller=true, kind=Service, name=demo-service-137, uid=a217ed19-750b-4bbd-964b-136ea9e8f45b, additionalProperties={})], resourceVersion=31971774, selfLink=null, uid=30a53db3-ecd9-4227-9605-900483b38a3f, additionalProperties={}), ports=[EndpointPort(appProtocol=null, name=http, port=8080, protocol=TCP, additionalProperties={})], additionalProperties={})
//                        newObj: EndpointSlice(addressType=IPv4, apiVersion=discovery.k8s.io/v1, endpoints=[Endpoint(addresses=[10.244.0.7], conditions=EndpointConditions(ready=true, serving=true, terminating=false, additionalProperties={}), deprecatedTopology={}, hints=null, hostname=null, nodeName=sit001, targetRef=ObjectReference(apiVersion=null, fieldPath=null, kind=Pod, name=demo-service-137-74cb7ccb66-hg4kt, namespace=test-ns, resourceVersion=null, uid=9f22f3bd-1af2-4824-bab3-40b555dd1f9f, additionalProperties={}), zone=cn-hangzhou-g, additionalProperties={})], kind=EndpointSlice, metadata=ObjectMeta(annotations={endpoints.kubernetes.io/last-change-trigger-time=2026-02-11T06:47:23Z}, creationTimestamp=2026-01-02T08:11:41Z, deletionGracePeriodSeconds=null, deletionTimestamp=null, finalizers=[], generateName=demo-service-137-, generation=9965, labels={endpointslice.kubernetes.io/managed-by=endpointslice-controller.k8s.io, kubernetes.io/service-name=demo-service-137}, managedFields=[ManagedFieldsEntry(apiVersion=discovery.k8s.io/v1, fieldsType=FieldsV1, fieldsV1=FieldsV1(additionalProperties={f:addressType={}, f:endpoints={}, f:metadata={f:annotations={.={}, f:endpoints.kubernetes.io/last-change-trigger-time={}}, f:generateName={}, f:labels={.={}, f:endpointslice.kubernetes.io/managed-by={}, f:kubernetes.io/service-name={}}, f:ownerReferences={.={}, k:{"uid":"a217ed19-750b-4bbd-964b-136ea9e8f45b"}={}}}, f:ports={}}), manager=kube-controller-manager, operation=Update, subresource=null, time=2026-02-11T06:47:23Z, additionalProperties={})], name=demo-service-137-8c9mx, namespace=test-ns, ownerReferences=[OwnerReference(apiVersion=v1, blockOwnerDeletion=true, controller=true, kind=Service, name=demo-service-137, uid=a217ed19-750b-4bbd-964b-136ea9e8f45b, additionalProperties={})], resourceVersion=31972063, selfLink=null, uid=30a53db3-ecd9-4227-9605-900483b38a3f, additionalProperties={}), ports=[EndpointPort(appProtocol=null, name=http, port=8080, protocol=TCP, additionalProperties={})], additionalProperties={})
                val addresses = ((oldObj?.endpoints?.map { it.addresses.first() }
                    ?: emptyList()) + (newObj?.endpoints?.map { it.addresses.first() } ?: emptyList()))
                    .toSet()

                // 满足之前是 ready 现在 unready
                val comeUnreadyAddresses = addresses.filter {
                    oldObj?.endpoints?.find { ep -> ep.addresses.contains(it) }
                        ?.conditions?.ready == true
                            && newObj?.endpoints?.find { ep -> ep.addresses.contains(it) }
                        ?.conditions?.notReadyAndNotTerminating() == true
                }
                if (comeUnreadyAddresses.isNotEmpty()) {
                    logger.debug {
                        "$comeUnreadyAddresses 变得不稳定了"
                    }
                    comeUnreadyAddresses.forEach {
                        callback(it, oldObj!!, newObj!!)
                    }
                }
            }

            override fun onDelete(obj: EndpointSlice?, deletedFinalStateUnknown: Boolean) {
            }
        })
}

private fun EndpointConditions.notReadyAndNotTerminating(): Boolean {
    return this.ready == false && this.terminating == false
}