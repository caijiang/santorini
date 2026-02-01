import { serviceApi, ServiceConfigData } from '../apis/service';
import { kubeServiceApi } from '../apis/kubernetes/service';
import { CUEnv } from '../apis/env';
import { describe, expect } from 'vitest';
import _ from 'lodash';
import { isAction } from '@reduxjs/toolkit';
import { deployToKubernetes } from './deployService';
import { deploymentApi } from '../apis/deployment';

const mockResources = {
  cpu: {
    requestMillis: 100,
    limitMillis: 200,
  },
  memory: {
    requestMiB: 32,
    limitMiB: 64,
  },
};

const demoServiceData = {
  id: 'demo',
  name: '范本服务',
  type: 'JVM',
  ports: [
    {
      number: 80,
      name: 'http',
    },
  ],
} satisfies ServiceConfigData;

const env = {
  // kubeMeta: {},
  id: 'online',
  name: '',
  production: false,
} satisfies CUEnv;

/**
 * 入参的arg是否符合，我们模拟 endpoint initiate 返回的结果
 * @param arg 调用方法的参数
 * @param api query api
 */
function invocationArgMatchMockEndpointInitiateV4(
  arg: any,
  api: {
    initiate: (a1: any) => any;
  }
): boolean {
  const mockApiInvokeAgainArg = api.initiate({});
  if (arg == mockApiInvokeAgainArg) return true;
  if (_.isPlainObject(arg) && _.isPlainObject(mockApiInvokeAgainArg)) {
    const { method } = arg;
    const { method: method2 } = mockApiInvokeAgainArg;
    return method == method2;
  }
  return false;
}

describe('部署服务', () => {
  const getState = vi.fn();
  // import { kubeServiceApi } from '../apis/kubernetes/service';
  // 1️⃣ 全局 mock
  vi.mock('../apis/deployment', () => {
    return {
      deploymentApi: {
        endpoints: {
          reportDeployResult: {
            initiate: vi.fn(() => 'reportDeployResult'),
          },
          deploy: {
            initiate: vi.fn(() => 'deployToServer'),
          },
          lastRelease: {
            initiate: vi.fn(
              ({ serviceId }: { serviceId: string; envId: string }) => {
                return 'lastRelease-' + serviceId;
              }
            ),
          },
        },
      },
    };
  });
  vi.mock('../apis/service', () => {
    return {
      serviceApi: {
        endpoints: {
          serviceById: {
            initiate: vi.fn((id: string) => {
              return 'serviceById-' + id;
            }),
          },
        },
      },
    };
  });
  vi.mock('../apis/kubernetes/service', () => {
    return {
      kubeServiceApi: {
        endpoints: {
          createDeployment: {
            initiate: vi.fn(() => 'createDeployment'),
          },
          patchDeployment: {
            initiate: (arg: any) => ({
              method: 'patchDeployment',
              args: arg,
            }),
          },
          createService: {
            initiate: vi.fn(() => 'createService'),
          },
          deleteService: {
            initiate: vi.fn(() => 'deleteService'),
          },
          serviceByName: {
            initiate: vi.fn(() => 'serviceByName'),
          },
          deployment: {
            initiate: vi.fn(() => 'deployment'),
          },
        },
      },
    };
  });
  // 通用 dispatch
  const commonDispatch = vi.fn((arg) => {
    // 反正不关心
    if (arg == kubeServiceApi.endpoints.createService.initiate({})) {
      return {
        unwrap: () => Promise.resolve(undefined),
      };
    }
    if (arg == kubeServiceApi.endpoints.createDeployment.initiate({})) {
      return {
        unwrap: () =>
          Promise.resolve({
            metadata: {
              resourceVersion: 'kubeDeployVersion',
            },
          }),
      };
    }
    if (arg == kubeServiceApi.endpoints.deployment.initiate({})) {
      return {
        unwrap: () => Promise.resolve(undefined),
      };
    }
    if (arg == kubeServiceApi.endpoints.serviceByName.initiate({})) {
      return {
        unwrap: () => Promise.resolve(undefined),
      };
    }
    if (arg == deploymentApi.endpoints.deploy.initiate({} as any)) {
      return {
        unwrap: () => Promise.resolve('deploymentId'),
      };
    }
    if (arg == deploymentApi.endpoints.reportDeployResult.initiate({} as any)) {
      return {
        unwrap: () => Promise.resolve(undefined),
      };
    }
    if (arg == kubeServiceApi.endpoints.deleteService.initiate({})) {
      return {
        unwrap: () => Promise.resolve(),
      };
    }
    if (arg == serviceApi.endpoints.serviceById.initiate('demo')) {
      return {
        unwrap: () => Promise.resolve(demoServiceData),
      };
    }
    if (
      arg ==
      deploymentApi.endpoints.lastRelease.initiate({
        serviceId: 'demo',
        envId: 'online',
      })
    ) {
      return {
        unwrap: () =>
          Promise.resolve({
            imageRepository: 'nginx',
            // imageTag?: string;
            pullSecretName: [],
          }),
      };
    }
    if (_.isFunction(arg)) {
      return arg(commonDispatch, getState, undefined);
    }
    if (isAction(arg)) {
      return undefined;
    }
    console.warn('unknown dispatch target:', arg);
    return undefined;
  });
  it(
    '一次正向的首次部署',
    {
      skip: false,
    },
    async () => {
      const action = deployToKubernetes({
        service: demoServiceData,
        env,
        deployData: {
          imageRepository: 'myImage',
          resources: mockResources,
        },
      });
      // console.warn('action:', action);

      // 要先 mock 返回值
      await action(commonDispatch, getState, undefined);
      console.log(
        'All calls:',
        JSON.stringify(commonDispatch.mock.calls, null, 2)
      );
      expect(commonDispatch.mock.calls.length, '一共 dispatch了').equals(7);
      expect(commonDispatch, '第一个是标准 pending').toHaveBeenNthCalledWith(
        1,
        expect.objectContaining({ type: 'service/deployToKubernetes/pending' })
      );
      expect(commonDispatch, '业务最终成功').toHaveBeenNthCalledWith(
        commonDispatch.mock.calls.length,
        expect.objectContaining({
          type: 'service/deployToKubernetes/fulfilled',
        })
      );
    }
  );
  it('在已部署的情况下重复部署(所谓升级)', { skip: false }, async () => {
    const action = deployToKubernetes({
      service: demoServiceData,
      env,
      lastDeploy: {
        imageRepository: 'myOldImage',
        resources: mockResources,
        serviceDataSnapshot: JSON.stringify({
          ...demoServiceData,
        }),
      },
      deployData: {
        imageRepository: 'myImage',
        resources: mockResources,
      },
    });
    const dispatch = vi.fn((arg) => {
      if (
        invocationArgMatchMockEndpointInitiateV4(
          arg,
          kubeServiceApi.endpoints.deployment
        )
      ) {
        return {
          unwrap: () => Promise.resolve({}),
        };
      }
      if (
        invocationArgMatchMockEndpointInitiateV4(
          arg,
          kubeServiceApi.endpoints.patchDeployment
        )
      ) {
        return {
          unwrap: () =>
            Promise.resolve({
              metadata: {
                resourceVersion: 'kubeDeployUpdatedVersion',
              },
            }),
        };
      }
      return commonDispatch(arg);
    });
    await action(dispatch, getState, undefined);
    console.log('All calls:', JSON.stringify(dispatch.mock.calls, null, 2));
    expect(dispatch.mock.calls.length, '一共 dispatch了').equals(7);
    expect(dispatch, '第一个是标准 pending').toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ type: 'service/deployToKubernetes/pending' })
    );
    expect(dispatch, '业务最终成功').toHaveBeenNthCalledWith(
      dispatch.mock.calls.length,
      expect.objectContaining({
        type: 'service/deployToKubernetes/fulfilled',
      })
    );
  });
  it('升级但只修改了环境', { skip: false }, async () => {
    const action = deployToKubernetes({
      service: demoServiceData,
      env,
      lastDeploy: {
        imageRepository: 'myOldImage',
        resources: mockResources,
        serviceDataSnapshot: JSON.stringify({
          ...demoServiceData,
        }),
      },
      deployData: {
        imageRepository: 'myOldImage',
        resources: mockResources,
      },
    });
    const dispatch = vi.fn((arg) => {
      if (
        invocationArgMatchMockEndpointInitiateV4(
          arg,
          kubeServiceApi.endpoints.deployment
        )
      ) {
        return {
          unwrap: () => Promise.resolve({}),
        };
      }

      if (
        invocationArgMatchMockEndpointInitiateV4(
          arg,
          kubeServiceApi.endpoints.patchDeployment
        )
      ) {
        return {
          unwrap: () =>
            Promise.resolve({
              metadata: {
                resourceVersion: 'kubeDeployUpdatedVersion',
              },
            }),
        };
      }
      return commonDispatch(arg);
    });
    await action(dispatch, getState, undefined);
    console.log('All calls:', JSON.stringify(dispatch.mock.calls, null, 2));
    expect(dispatch.mock.calls.length, '一共 dispatch了').equals(7);
    expect(dispatch, '第一个是标准 pending').toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ type: 'service/deployToKubernetes/pending' })
    );
    expect(dispatch, '业务最终成功').toHaveBeenNthCalledWith(
      dispatch.mock.calls.length,
      expect.objectContaining({
        type: 'service/deployToKubernetes/fulfilled',
      })
    );
  });
});
