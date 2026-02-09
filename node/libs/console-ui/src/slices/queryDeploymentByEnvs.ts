import { createLoopRTKQueryThunk } from './support/loopRTKQuery';
import { CUEnv } from '../apis/env';
import { NamespacedNamedResource } from '../apis/kubernetes/type';
import { IDeployment } from 'kubernetes-models/apps/v1/Deployment';
import { kubeServiceApi } from '../apis/kubernetes/service';

export const queryDeploymentByEnvs = createLoopRTKQueryThunk<
  CUEnv,
  string,
  NamespacedNamedResource,
  IDeployment
>(
  'tools/queryDeploymentByEnvs',
  (it) => it.id,
  kubeServiceApi.endpoints.getDeployments
);
