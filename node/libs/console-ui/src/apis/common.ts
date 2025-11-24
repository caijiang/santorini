import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi } from './kubernetes';
import {
  KubeNamespaceListProps,
  KubeSecretListProps,
} from 'cdk8s-plus-33/lib/imports/k8s';

export const commonApi = createApi({
  reducerPath: 'kubeCommon',
  baseQuery: kubeBaseApi,
  // tagTypes: ['kubernetesJWTToken'],
  endpoints: (build) => {
    return {
      secretByNamespace: build.query<KubeSecretListProps, string>({
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

export const { useNamespacesQuery } = commonApi;
