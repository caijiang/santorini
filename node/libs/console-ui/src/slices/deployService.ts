import { createAsyncThunk, GetThunkAPI } from '@reduxjs/toolkit';
import { EnvRelatedServiceResource, ServiceConfigData } from '../apis/service';
import { CUEnv } from '../apis/env';
import { kubeServiceApi } from '../apis/kubernetes/service';
import yamlGenerator from '../apis/kubernetes/yamlGenerator';
import { IDeployment } from 'kubernetes-models/apps/v1/Deployment';

export interface ServiceDeployToKubernetesProps {
  service: ServiceConfigData;
  env: CUEnv;
  envRelated: EnvRelatedServiceResource;
}

/**
 * 我们的服务实例在 kubernetes 的表述，有可能是 deployment 也有可能是其他
 */
interface ServiceInstanceInKubernetes {
  deployment?: IDeployment;
}

async function findServiceInstanceInKubernetes(
  serviceId: string,
  envId: string,
  dispatch: GetThunkAPI<any>['dispatch']
): Promise<ServiceInstanceInKubernetes | undefined> {
  const rs = await dispatch(
    kubeServiceApi.endpoints.deployments.initiate({
      namespace: envId,
      labelSelectors: [
        // 'santorini.io/manageable=true'
        'santorini.io/service-type',
        `santorini.io/id=${serviceId}`,
      ],
    })
  ).unwrap();
  if (rs.length > 0) {
    return {
      deployment: rs[0],
    };
  }
  return undefined;
}

/**
 * 支持新部署，也支持更新
 */
export const deployToKubernetes = createAsyncThunk(
  'service/deployToKubernetes',
  async (input: ServiceDeployToKubernetesProps, { dispatch }) => {
    const { service, env } = input;
    const current = await findServiceInstanceInKubernetes(
      service.id,
      env.id,
      dispatch
    );
    console.debug('current service instance: ', current);
    const yaml = yamlGenerator.serviceInstance(input);
    console.debug('yaml:', yaml);
    if (current) {
      // 更新
      await dispatch(
        kubeServiceApi.endpoints.updateDeployment.initiate({
          namespace: env.id,
          yaml: yaml.deployment,
          name: service.id,
        })
      ).unwrap();
    } else {
      // 部署
      await dispatch(
        kubeServiceApi.endpoints.createDeployment.initiate({
          namespace: env.id,
          yaml: yaml.deployment,
        })
      ).unwrap();
    }
  }
);
