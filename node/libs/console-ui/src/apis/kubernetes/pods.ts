import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi, queryFunctionForList } from './kubernetes';
import { NamespaceWithLabelSelectors, toPodsLabelSelectors } from './type';
import { IReplicaSet, IReplicaSetList } from 'kubernetes-models/apps/v1';
import { IPod } from 'kubernetes-models/v1';

export const kubePodsApi = createApi({
  reducerPath: 'kubePods',
  baseQuery: kubeBaseApi,
  tagTypes: ['Pods'],
  endpoints: (build) => ({
    replicaset: build.query<
      IReplicaSet[],
      NamespaceWithLabelSelectors,
      IReplicaSetList
    >({
      transformResponse(baseQueryReturnValue) {
        return baseQueryReturnValue.items || [];
      },
      // // app.kubernetes.io/name=demo-service
      // /api/v1/deployment/test-ns/demo-service/newreplicaset
      query: ({ name, namespace }) => ({
        url: `/apis/apps/v1/namespaces/${namespace}/replicasets`,
        params: {
          labelSelector: `app.kubernetes.io/name=${name}`,
        },
      }),
    }),
    pods: build.query<IPod[], NamespaceWithLabelSelectors>({
      providesTags: ['Pods'],
      // 查询函数 - 自动处理分页
      async queryFn(args, _api, _extraOptions, baseQuery) {
        return queryFunctionForList(
          () => {
            const { namespace } = args;
            const params: Record<string, string> = {};
            params.labelSelector = toPodsLabelSelectors(args);
            // 构建 URL
            const url = !namespace
              ? '/api/v1/pods'
              : `/api/v1/namespaces/${namespace}/pods`;
            return { url, params };
          },
          _api,
          _extraOptions,
          baseQuery
        );
      },

      // 为所有 Pod 提供相同的标签，便于缓存失效
      // providesTags: (result) =>
      //   result
      //     ? [
      //         ...result.map(({ metadata }) => ({
      //           type: 'Pod' as const,
      //           id: `${metadata?.namespace}/${metadata?.name}`,
      //         })),
      //         { type: 'Pod', id: 'LIST' },
      //       ]
      //     : [{ type: 'Pod', id: 'LIST' }],
    }),
  }),
});

export const { usePodsQuery } = kubePodsApi;
