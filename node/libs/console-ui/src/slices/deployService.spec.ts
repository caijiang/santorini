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
  kubeMeta: {},
  id: 'online',
  name: '',
  production: false,
} satisfies CUEnv;

describe('部署服务', () => {
  it(
    '正向流程',
    {
      skip: false,
    },
    async () => {
      const action = deployToKubernetes({
        service: demoServiceData,
        env,
        envRelated: {
          imageRepository: 'myImage',
        },
      });
      console.warn('action:', action);

      const getState = vi.fn();
      // import { kubeServiceApi } from '../apis/kubernetes/service';
      // 1️⃣ 全局 mock
      vi.mock('../apis/service', () => {
        return {
          serviceApi: {
            endpoints: {
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
              deployments: {
                initiate: vi.fn(() => {
                  return 'deployments';
                }),
              },
            },
          },
        };
      });
      const dispatch = vi.fn((arg) => {
        if (
          arg ==
          kubeServiceApi.endpoints.deployments.initiate({
            namespace: '',
          })
        ) {
          return {
            unwrap: () => Promise.resolve([]),
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
          return arg(dispatch, getState, undefined);
        }
        if (isAction(arg)) {
          return undefined;
        }
        console.warn('unknown dispatch target:', arg);
        return undefined;
      });
      // 要先 mock 返回值
      await action(dispatch, getState, undefined);
      console.log('All calls:', JSON.stringify(dispatch.mock.calls, null, 2));
      expect(dispatch).toHaveBeenNthCalledWith(
        1,
        expect.objectContaining({ type: 'service/deployToKubernetes/pending' })
      );
      expect(dispatch).toHaveBeenNthCalledWith(
        3,
        expect.objectContaining({
          type: 'service/deployToKubernetes/fulfilled',
        })
      );
    }
  );
});
