import { describe, expect } from 'vitest';
import { CUEnv } from '../apis/env';
import { startDeployServiceToEnv } from './service';
import { serviceApi, ServiceConfigData } from '../apis/service';
import { isAction } from '@reduxjs/toolkit';
import _ from 'lodash';

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
// const env = vi.

// export const actionLogger = (store) => (next) => (action) => {
//   console.log('🟦 ACTION:', action.type, 'payload:', action.payload);
//   return next(action);
// };
//
// const store = configureStore({
//   middleware: (getDefaultMiddleware) =>
//     getDefaultMiddleware({})
//       .concat(middlewares([module]))
//       .concat(actionLogger),
//   reducer: {
//     ...reducers([module]),
//   },
// });
//
// storeCallback([module], store);

describe('服务', () => {
  it(
    '如果传入字符串也是可以的',
    {
      skip: false,
    },
    async () => {
      const action = startDeployServiceToEnv({
        service: 'demo',
        env,
      });
      console.warn('action:', action);

      const getState = vi.fn();
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
      const dispatch = vi.fn((arg) => {
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
        expect.objectContaining({ type: 'service/deploy/pending' })
      );
      expect(dispatch).toHaveBeenNthCalledWith(
        5,
        expect.objectContaining({ type: 'service/deploy/p2/pending' })
      );
    }
  );
  // it('deployServiceToEnv', async () => {
  //   const action = startDeployServiceToEnv({
  //     service: demoServiceData,
  //     env,
  //   });
  //   const dispatch = vi.fn();
  //   const getState = vi.fn();
  //   await action(dispatch, getState, undefined);
  // });
});
