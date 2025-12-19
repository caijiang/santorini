import { serviceApi, ServiceConfigData } from '../apis/service';
import { kubeServiceApi } from '../apis/kubernetes/service';
import { CUEnv } from '../apis/env';
import { describe } from 'vitest';
import _ from 'lodash';
import { isAction } from '@reduxjs/toolkit';
import { deployToKubernetes } from './deployService';

const demoServiceData = {
  id: 'demo',
  name: '范本服务',
  type: 'JVM',
  resources: {
    cpu: {
      requestMillis: 100,
      limitMillis: 200,
    },
    memory: {
      requestMiB: 32,
      limitMiB: 64,
    },
  },
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

describe('部署服务', () => {
  const getState = vi.fn();
  // import { kubeServiceApi } from '../apis/kubernetes/service';
  // 1️⃣ 全局 mock
  vi.mock('../apis/service', () => {
    return {
      serviceApi: {
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
            initiate: vi.fn(() => 'patchDeployment'),
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
    if (arg == serviceApi.endpoints.deploy.initiate({} as any)) {
      return {
        unwrap: () => Promise.resolve('deploymentId'),
      };
    }
    if (arg == serviceApi.endpoints.reportDeployResult.initiate({} as any)) {
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
      serviceApi.endpoints.lastRelease.initiate({
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
        },
      });
      // console.warn('action:', action);

      // 要先 mock 返回值
      await action(commonDispatch, getState, undefined);
      console.log(
        'All calls:',
        JSON.stringify(commonDispatch.mock.calls, null, 2)
      );
      expect(commonDispatch).toHaveBeenNthCalledWith(
        1,
        expect.objectContaining({ type: 'service/deployToKubernetes/pending' })
      );
      expect(commonDispatch).toHaveBeenNthCalledWith(
        8,
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
        serviceDataSnapshot: JSON.stringify({
          ...demoServiceData,
          resources: {
            cpu: {
              requestMillis: 99, // 新的会是 99
              limitMillis: 200,
            },
            memory: {
              requestMiB: 32,
              limitMiB: 64,
            },
          },
        }),
      },
      deployData: {
        imageRepository: 'myImage',
      },
    });
    const dispatch = vi.fn((arg) => {
      if (arg == kubeServiceApi.endpoints.deployment.initiate({})) {
        return {
          unwrap: () => Promise.resolve({}),
        };
      }
      if (arg == kubeServiceApi.endpoints.patchDeployment.initiate({})) {
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
    expect(dispatch).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ type: 'service/deployToKubernetes/pending' })
    );
    expect(dispatch).toHaveBeenNthCalledWith(
      8,
      expect.objectContaining({
        type: 'service/deployToKubernetes/fulfilled',
      })
    );
  });
});
