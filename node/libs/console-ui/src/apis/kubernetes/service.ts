import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi } from './kubernetes';
import { IDeployment } from 'kubernetes-models/apps/v1/Deployment';
import { IService } from 'kubernetes-models/v1';
import { NamespaceWithLabelSelectors, TypedObjectContainer } from './type';
import {
  DaemonSet,
  IDaemonSet,
  IDaemonSetList,
} from 'kubernetes-models/apps/v1';
import YAML from 'yaml';
import { createCrudApisForNamespacedResources } from './crud';

export const kubeServiceApi = createApi({
  reducerPath: 'kubeService',
  baseQuery: kubeBaseApi,
  tagTypes: ['deployments', 'Deployments', 'DaemonSets', 'Services'],
  endpoints: (build) => {
    return {
      ...createCrudApisForNamespacedResources<IService, 'Services'>(
        'Services',
        'v1',
        build
      ),
      ...createCrudApisForNamespacedResources<IDeployment, 'Deployments'>(
        'Deployments',
        'apps/v1',
        build
      ),
      createDaemonSet: build.mutation<
        undefined,
        TypedObjectContainer<IDaemonSet>
      >({
        invalidatesTags: ['DaemonSets'],
        query: ({ data, namespace }) => ({
          url: `/apis/apps/v1/namespaces/${namespace}/daemonsets`,
          method: 'POST',
          body: YAML.stringify(new DaemonSet(data).toJSON()),
          headers: {
            'Content-Type': 'application/yaml',
          },
        }),
      }),
      daemonSets: build.query<
        IDaemonSet[],
        NamespaceWithLabelSelectors,
        IDaemonSetList
      >({
        providesTags: ['DaemonSets'],
        transformResponse: (baseQueryReturnValue, _, { name }) => {
          if (name) {
            const x = baseQueryReturnValue as unknown as IDaemonSet | undefined;
            return x ? [x] : [];
          }
          return baseQueryReturnValue?.items;
        },
        query: ({ namespace, labelSelectors, name }) => ({
          url: name
            ? `/apis/apps/v1/namespaces/${namespace}/daemonsets/${name}`
            : namespace
            ? `/apis/apps/v1/namespaces/${namespace}/daemonsets`
            : '/apis/apps/v1/daemonsets',
          params: {
            labelSelector:
              labelSelectors?.join(',') ?? 'santorini.io/manageable=true',
          },
        }),
      }),
    };
  },
});

export const {
  useGetDeploymentsQuery,
  useDaemonSetsQuery,
  useCreateDaemonSetMutation,
} = kubeServiceApi;
