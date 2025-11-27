import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi } from './kubernetes';
import {
  KubeNamespaceListProps,
  KubeSecretListProps,
  KubeSecretProps,
} from 'cdk8s-plus-33/lib/imports/k8s';

export const commonApi = createApi({
  reducerPath: 'kubeCommon',
  baseQuery: kubeBaseApi,
  // tagTypes: ['kubernetesJWTToken'],
  endpoints: (build) => {
    return {
      secretByNamespace: build.query<
        KubeSecretProps[],
        string,
        KubeSecretListProps
      >({
        transformResponse: (baseQueryReturnValue) => {
          return baseQueryReturnValue?.items ?? [];
        },
        query: (arg) => ({
          url: `/api/v1/namespaces/${arg}/secrets`,
          params: {
            labelSelector: 'santorini.io/manageable=true',
          },
        }),
      }),
      namespaces: build.query<KubeNamespaceListProps, undefined>({
        // providesTags: ['kubernetesJWTToken'],
        query: () => ({
          url: '/api/v1/namespaces',
          params: {
            labelSelector: 'santorini.io/manageable=true',
          },
        }),
      }),
    };
  },
});

export const { useNamespacesQuery, useSecretByNamespaceQuery } = commonApi;
