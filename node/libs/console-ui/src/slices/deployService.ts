import { createAsyncThunk, GetThunkAPI } from '@reduxjs/toolkit';
import {
  DeploymentDeployData,
  serviceApi,
  ServiceConfigData,
} from '../apis/service';
import { CUEnv } from '../apis/env';
import { kubeServiceApi } from '../apis/kubernetes/service';
import yamlGenerator from '../apis/kubernetes/yamlGenerator';
import { IDeployment } from 'kubernetes-models/apps/v1/Deployment';
import { IService } from 'kubernetes-models/v1';

export interface ServiceDeployToKubernetesProps {
  service: ServiceConfigData;
  env: CUEnv;
  envRelated: DeploymentDeployData;
}

/**
 * 我们的服务实例在 kubernetes 的表述，有可能是 deployment 也有可能是其他
 */
interface ServiceInstanceInKubernetes {
  deployment?: IDeployment;
  /**
   * 是指 kubernetes 的服务
   */
  service?: IService;
}

async function findServiceInstanceInKubernetes(
  serviceId: string,
  envId: string,
  dispatch: GetThunkAPI<any>['dispatch']
): Promise<ServiceInstanceInKubernetes | undefined> {
  const rs0 = await dispatch(
    kubeServiceApi.endpoints.deployment.initiate({
      namespace: envId,
      name: serviceId,
      labelSelectors: ['santorini.io/service-type'],
    })
  ).unwrap();
  const ss = await dispatch(
    kubeServiceApi.endpoints.serviceByName.initiate({
      namespace: envId,
      name: serviceId,
    })
  ).unwrap();
  if (!ss && !rs0) {
    return undefined;
  }
  return {
    deployment: rs0,
    service: ss,
  };
}

/**
 * 支持新部署，也支持更新
 */
export const deployToKubernetes = createAsyncThunk(
  'service/deployToKubernetes',
  async (input: ServiceDeployToKubernetesProps, { dispatch }) => {
    const { service, env } = input;

    // 先跟服务器交互
    // 最好是可以获取到一个 id; 然后拿到新部署后的 id 映射过去
    // 如果失败，则直接删除
    const result = await dispatch(
      serviceApi.endpoints.deploy.initiate({
        envId: env.id,
        serviceId: service.id,
        data: input.envRelated,
      })
    ).unwrap();

    console.debug('服务端部署结果:', result);

    const current = await findServiceInstanceInKubernetes(
      service.id,
      env.id,
      dispatch
    );
    console.debug('current service instance: ', current);
    const yaml = yamlGenerator.serviceInstance(input);
    console.debug('yaml:', yaml);
    let deploymentResult: IDeployment | undefined = undefined;
    if (current) {
      // 更新
      if (yaml.deployment) {
        if (current.deployment) {
          deploymentResult = await dispatch(
            kubeServiceApi.endpoints.updateDeployment.initiate({
              namespace: env.id,
              yaml: yaml.deployment,
              name: service.id,
            })
          ).unwrap();
        } else {
          deploymentResult = await dispatch(
            kubeServiceApi.endpoints.createDeployment.initiate({
              namespace: env.id,
              yaml: yaml.deployment,
            })
          ).unwrap();
        }
      } else {
        // TODO never
      }

      if (yaml.service) {
        if (!current.service) {
          await dispatch(
            kubeServiceApi.endpoints.createService.initiate({
              namespace: env.id,
              yaml: yaml.service,
            })
          ).unwrap();
        } else {
          await dispatch(
            kubeServiceApi.endpoints.updateService.initiate({
              namespace: env.id,
              yaml: yaml.service,
              name: service.id,
            })
          ).unwrap();
        }
      } else {
        if (current.service) {
          await dispatch(
            kubeServiceApi.endpoints.deleteService.initiate({
              namespace: env.id,
              name: service.id,
            })
          ).unwrap();
        }
      }
    } else {
      // 部署
      deploymentResult = await dispatch(
        kubeServiceApi.endpoints.createDeployment.initiate({
          namespace: env.id,
          yaml: yaml.deployment,
        })
      ).unwrap();
      if (yaml.service) {
        await dispatch(
          kubeServiceApi.endpoints.createService.initiate({
            namespace: env.id,
            yaml: yaml.service,
          })
        ).unwrap();
      }
    }
    console.debug('kubernetes 部署结果:', deploymentResult);
  }
);
