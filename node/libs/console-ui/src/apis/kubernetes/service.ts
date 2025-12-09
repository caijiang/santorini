import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi } from './kubernetes';
import { IDeploymentList } from 'kubernetes-models/apps/v1/DeploymentList';
import { IDeployment } from 'kubernetes-models/apps/v1/Deployment';
import { IService } from 'kubernetes-models/v1';
import {
  NamespacedNamedResource,
  NamespaceWithLabelSelectors,
  ObjectContainer,
  TypedObjectContainer,
} from './type';
import {
  DaemonSet,
  IDaemonSet,
  IDaemonSetList,
} from 'kubernetes-models/apps/v1';
import YAML from 'yaml';

export const kubeServiceApi = createApi({
  reducerPath: 'kubeService',
  baseQuery: kubeBaseApi,
  tagTypes: ['deployments', 'services', 'DaemonSets'],
  endpoints: (build) => {
    return {
      updateService: build.mutation<
        undefined,
        ObjectContainer & { name: string }
      >({
        invalidatesTags: ['services'],
        query: ({ namespace, yaml, name }) => ({
          url: `/api/v1/namespaces/${namespace}/services/${name}`,
          method: 'PUT',
          body: yaml,
          headers: {
            'Content-Type': 'application/yaml',
          },
        }),
      }),
      createService: build.mutation<undefined, ObjectContainer>({
        invalidatesTags: ['services'],
        query: ({ namespace, yaml }) => ({
          url: `/api/v1/namespaces/${namespace}/services`,
          method: 'POST',
          body: yaml,
          headers: {
            'Content-Type': 'application/yaml',
          },
        }),
      }),
      deleteService: build.mutation<
        IService | undefined,
        NamespacedNamedResource
      >({
        invalidatesTags: ['services'],
        query: ({ namespace, name }) => ({
          url: `/api/v1/namespaces/${namespace}/services/${name}`,
          method: 'DELETE',
        }),
      }),
      serviceByName: build.query<IService | undefined, NamespacedNamedResource>(
        {
          // transformResponse: (baseQueryReturnValue) => {
          //   return baseQueryReturnValue?.items ?? [];
          // },
          providesTags: ['services'],
          query: ({ namespace, name }) => ({
            url: `/api/v1/namespaces/${namespace}/services/${name}`,
          }),
        }
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
      updateDeployment: build.mutation<
        IDeployment,
        ObjectContainer & { name: string }
      >({
        invalidatesTags: ['deployments'],
        query: ({ namespace, yaml, name }) => ({
          url: `/apis/apps/v1/namespaces/${namespace}/deployments/${name}`,
          method: 'PUT',
          body: yaml,
          headers: {
            'Content-Type': 'application/yaml',
          },
        }),
      }),
      createDeployment: build.mutation<IDeployment, ObjectContainer>({
        invalidatesTags: ['deployments'],
        query: ({ namespace, yaml }) => ({
          url: `/apis/apps/v1/namespaces/${namespace}/deployments`,
          method: 'POST',
          body: yaml,
          headers: {
            'Content-Type': 'application/yaml',
          },
        }),
      }),
      // TODO: 暂时部署只以 deployment 进行
      deployments: build.query<
        IDeployment[],
        NamespaceWithLabelSelectors,
        IDeploymentList
      >({
        transformResponse: (baseQueryReturnValue) => {
          return baseQueryReturnValue?.items ?? [];
        },
        providesTags: ['deployments'],
        query: ({ namespace, labelSelectors }) => ({
          url: namespace
            ? `/apis/apps/v1/namespaces/${namespace}/deployments`
            : '/apis/apps/v1/deployments',
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
  useDeploymentsQuery,
  useDaemonSetsQuery,
  useCreateDaemonSetMutation,
} = kubeServiceApi;
