import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi } from './kubernetes';
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
      patchDeployment: build.mutation<IDeployment, ObjectContainer>({
        invalidatesTags: ['deployments'],
        query: ({ namespace, jsonObject, name }) => ({
          url: `/apis/apps/v1/namespaces/${namespace}/deployments/${name}`,
          method: 'PATCH',
          body: JSON.stringify(jsonObject),
          headers: {
            // 'Content-Type': 'application/strategic-merge-patch+json',
            'Content-Type': 'application/json-patch+json',
          },
        }),
      }),
      updateDeployment: build.mutation<IDeployment, ObjectContainer>({
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
      // 这个作为纯纯的测试
      // https://kubernetes.io/zh-cn/docs/reference/using-api/api-concepts/#patch-and-apply
      // https://www.npmjs.com/package/json-merge-patch?activeTab=readme
      pd: build.mutation({
        query: () => ({
          url: '/apis/apps/v1/namespaces/test-ns/deployments/patch-test-obj',
          method: 'PATCH',
          headers: {
            'Content-Type': 'application/strategic-merge-patch+json',
          },
          // 在一个已存在的对象中 插入一个新属性
          // body: JSON.stringify({
          //   metadata: {
          //     newProperty1: 'v1',
          //   },
          // }),
          // 新增了一个 对象
          // body: JSON.stringify({
          //   metadata: {
          //     labels: {
          //       newProperty1: 'v1',
          //     },
          //   },
          // }),
          // 修改一个现有对象的值
          // body: JSON.stringify({
          //   metadata: {
          //     labels: {
          //       newProperty1: 'v2',
          //     },
          //   },
          // }),
          // 在一个已存在的对象中 插入一个新属性
          body: JSON.stringify({
            metadata: {
              labels: {
                newProperty2: 'v1',
              },
            },
          }),
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
      /**
       * 单个获取,所有都是必须的
       */
      deployment: build.query<IDeployment, NamespaceWithLabelSelectors>({
        providesTags: ['deployments'],
        query: ({ namespace, labelSelectors, name }) => ({
          url: `/apis/apps/v1/namespaces/${namespace}/deployments/${name}`,
          params: {
            labelSelector:
              labelSelectors?.join(',') ?? 'santorini.io/manageable=true',
          },
        }),
      }),
      // TODO: 暂时部署只以 deployment 进行
      // deployments: build.query<
      //   IDeployment[],
      //   NamespaceWithLabelSelectors,
      //   IDeploymentList
      // >({
      //   transformResponse: (baseQueryReturnValue) => {
      //     return baseQueryReturnValue?.items ?? [];
      //   },
      //   providesTags: ['deployments'],
      //   query: ({ namespace, labelSelectors }) => ({
      //     url: namespace
      //       ? `/apis/apps/v1/namespaces/${namespace}/deployments`
      //       : '/apis/apps/v1/deployments',
      //     params: {
      //       labelSelector:
      //         labelSelectors?.join(',') ?? 'santorini.io/manageable=true',
      //     },
      //   }),
      // }),
    };
  },
});

export const {
  useDeploymentQuery,
  useDaemonSetsQuery,
  useCreateDaemonSetMutation,
} = kubeServiceApi;
