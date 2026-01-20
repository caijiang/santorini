import { createAsyncThunk, Dispatch, GetThunkAPI } from '@reduxjs/toolkit';
import { DeploymentDeployData, ServiceConfigData } from '../apis/service';
import { CUEnv } from '../apis/env';
import { kubeServiceApi } from '../apis/kubernetes/service';
import yamlGenerator from '../apis/kubernetes/yamlGenerator';
import { IDeployment } from 'kubernetes-models/apps/v1/Deployment';
import { IService } from 'kubernetes-models/v1';
import { compare } from 'fast-json-patch';
import { deploymentApi, usePreDeployMutation } from '../apis/deployment';
import { Rule } from 'eslint';
import { FieldProps } from 'rc-field-form/lib/Field';
import { dispatchThunkActionThrowIfError } from '../common/rtk';

export interface ServiceDeployToKubernetesProps {
  service: ServiceConfigData;
  env: CUEnv;
  /**
   * 这次要部署的数据
   */
  deployData: DeploymentDeployData;
  /**
   * 非首次部署的话还有这个，表示上次部署的情况
   */
  lastDeploy?: DeploymentDeployData;
  /**
   * 不要使用最新代码改动
   */
  doNotUseLatestCode?: boolean;
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

type Rules = Required<FieldProps['rules']>;
type ExtractRule<T> = T extends Array<infer U> ? U : never;
type Rule = ExtractRule<Rules>;

export function toImageRepository(input: string) {
  const index = input.lastIndexOf(':');
  return index == -1
    ? [input]
    : [input.substring(0, index), input.substring(index + 1)];
}

export function imageRule(
  serviceId: string,
  envId: string,
  pullSecretName: string[] | undefined,
  dispatch: Dispatch<any>,
  warnMessageConsumer?: (message: string) => void
): Rule {
  return {
    validator: async (_, value: string) => {
      if (!value) return Promise.resolve();
      const st = toImageRepository(value);
      const ps = await dispatchThunkActionThrowIfError(
        dispatch,
        deploymentApi.endpoints.preDeploy.initiate({
          serviceId,
          envId,
          data: {
            resources: {
              cpu: {
                requestMillis: 1,
                limitMillis: 2,
              },
              memory: {
                requestMiB: 1,
                limitMiB: 2,
              },
            },
            pullSecretName,
            imageRepository: st[0],
            imageTag: st.length > 1 ? st[1] : undefined,
          },
        })
      );
      if (ps.warnMessage) {
        warnMessageConsumer?.(ps.warnMessage);
      }
      if (!ps.imageDigest) {
        return Promise.reject(`镜像无法找到，可能原因：${ps.warnMessage}`);
      }
      if (!ps.imagePlatformMatch || !ps.successfulEnvs) {
        return Promise.reject(`镜像无法使用，可能原因：${ps.warnMessage}`);
      }
      return Promise.resolve();
    },
  };
}

// 有可能是输入了全部,
// imageRepository: string
// imageTag?: string
// pullSecretName?: string[]
export function useImageTagRule(
  serviceId: string,
  envId: string,
  currentData: DeploymentDeployData | undefined
): Rule {
  const [preApi] = usePreDeployMutation();
  return {
    validator: async (_, value: string) => {
      if (!currentData) return Promise.reject('尚未准备就绪');
      if (!value) return Promise.resolve();
      const ps = await preApi({
        serviceId,
        envId,
        data: {
          ...currentData,
          imageTag: value,
        },
      }).unwrap();

      if (!ps.imageDigest) {
        return Promise.reject(`镜像无法找到，可能原因：${ps.warnMessage}`);
      }
      if (!ps.imagePlatformMatch || !ps.successfulEnvs) {
        return Promise.reject(`镜像无法使用，可能原因：${ps.warnMessage}`);
      }
      return Promise.resolve();
    },
  };
}

/**
 * 支持新部署，也支持更新
 */
export const deployToKubernetes = createAsyncThunk(
  'service/deployToKubernetes',
  async (input: ServiceDeployToKubernetesProps, { dispatch }) => {
    const { service, env, lastDeploy } = input;

    console.debug('之前部署的概要信息: lastDeploy:', lastDeploy);
    // 如果失败，则直接删除
    const result = await dispatch(
      deploymentApi.endpoints.deploy.initiate({
        envId: env.id,
        serviceId: service.id,
        data: input.deployData,
      })
    ).unwrap();

    console.debug('服务端部署结果:', result);

    try {
      const current = await findServiceInstanceInKubernetes(
        service.id,
        env.id,
        dispatch
      );
      console.debug('当前 kube 已部署情况: ', current);
      const thisTimeVersion = yamlGenerator.serviceInstance(input);
      const lastVersion = lastDeploy?.serviceDataSnapshot
        ? yamlGenerator.serviceInstance({
            ...input,
            service: JSON.parse(lastDeploy.serviceDataSnapshot),
            deployData: lastDeploy,
            doNotUseLatestCode: true,
          })
        : undefined;
      console.debug('thisTimeVersion:', thisTimeVersion);
      console.debug('lastVersion:', lastVersion);
      let deploymentResult: IDeployment | undefined = undefined;
      if (current) {
        // 更新
        if (thisTimeVersion.deployment) {
          if (current.deployment) {
            const versionMergePatch = lastVersion
              ? compare(
                  lastVersion.deployment.toJsonObject(),
                  thisTimeVersion.deployment.toJsonObject()
                )
              : undefined;
            console.debug(
              '之前已经部署了 deployment,本次进行更新:',
              versionMergePatch
            );

            if (versionMergePatch) {
              // console.error("from: ",lastVersion?.deployment?.toJsonText())
              // console.error("to: ",thisTimeVersion.deployment.toJsonText())
              console.debug('另一个版本的闪存也在:', versionMergePatch);
              // console.error(JSON.stringify(versionMergePatch));
              deploymentResult = await dispatch(
                kubeServiceApi.endpoints.patchDeployment.initiate({
                  namespace: env.id,
                  jsonObject: versionMergePatch,
                  name: service.id,
                })
              ).unwrap();
            } else {
              deploymentResult = await dispatch(
                kubeServiceApi.endpoints.updateDeployment.initiate({
                  namespace: env.id,
                  yaml: thisTimeVersion.deployment.toYamlText(),
                  name: service.id,
                })
              ).unwrap();
            }
          } else {
            console.debug('首次部署 deployment');
            deploymentResult = await dispatch(
              kubeServiceApi.endpoints.createDeployment.initiate({
                namespace: env.id,
                yaml: thisTimeVersion.deployment.toYamlText(),
              })
            ).unwrap();
          }
        } else {
          // TODO never
        }

        if (thisTimeVersion.service) {
          if (!current.service) {
            await dispatch(
              kubeServiceApi.endpoints.createService.initiate({
                namespace: env.id,
                yaml: thisTimeVersion.service.toYamlText(),
              })
            ).unwrap();
          } else {
            await dispatch(
              kubeServiceApi.endpoints.updateService.initiate({
                namespace: env.id,
                yaml: thisTimeVersion.service.toYamlText(),
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
            yaml: thisTimeVersion.deployment.toYamlText(),
          })
        ).unwrap();
        if (thisTimeVersion.service) {
          await dispatch(
            kubeServiceApi.endpoints.createService.initiate({
              namespace: env.id,
              yaml: thisTimeVersion.service.toYamlText(),
            })
          ).unwrap();
        }
      }
      console.debug('kubernetes 部署结果:', deploymentResult);
      if (deploymentResult?.metadata?.generation !== undefined) {
        await dispatch(
          deploymentApi.endpoints.reportDeployResult.initiate({
            id: result,
            generation: `${deploymentResult?.metadata?.generation}`,
          })
        ).unwrap();
      }
    } catch (e) {
      console.log('部署发生了错误:', e);
      await dispatch(
        deploymentApi.endpoints.invokeDeploy.initiate(result)
      ).unwrap();
      throw e;
    }
  }
);
