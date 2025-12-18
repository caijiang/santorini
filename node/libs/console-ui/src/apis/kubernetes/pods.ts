import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi } from './kubernetes';
import { NamespaceWithLabelSelectors } from './type';
import { IReplicaSet, IReplicaSetList } from 'kubernetes-models/apps/v1';
import { IPod, IPodList } from 'kubernetes-models/v1';

export const kubePodsApi = createApi({
  reducerPath: 'kubePods',
  baseQuery: kubeBaseApi,
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
    pods: build.query<IPod[], NamespaceWithLabelSelectors, IPodList>({
      transformResponse(baseQueryReturnValue) {
        return baseQueryReturnValue.items || [];
      },
      query: ({ name, namespace }) => ({
        url: `/api/v1/namespaces/${namespace}/pods`,
        params: {
          labelSelector: `app.kubernetes.io/name=${name}`,
        },
      }),
    }),
  }),
});

export const { useReplicasetQuery, usePodsQuery } = kubePodsApi;
