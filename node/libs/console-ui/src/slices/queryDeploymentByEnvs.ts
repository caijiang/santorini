import { createLoopRTKQueryThunk } from './support/loopRTKQuery';
import { CUEnv } from '../apis/env';
import { NamespaceWithLabelSelectors } from '../apis/kubernetes/type';
import { IDeployment } from 'kubernetes-models/apps/v1/Deployment';
import { kubeServiceApi } from '../apis/kubernetes/service';

export const queryDeploymentByEnvs = createLoopRTKQueryThunk<
  CUEnv,
  string,
  NamespaceWithLabelSelectors,
  IDeployment
>(
  'tools/queryDeploymentByEnvs',
  (it) => it.id,
  kubeServiceApi.endpoints.deployment
);
