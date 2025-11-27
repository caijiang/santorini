import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi } from './kubernetes';
import {
  KubeDeploymentListProps,
  KubeDeploymentProps,
} from 'cdk8s-plus-33/lib/imports/k8s';

interface NamespaceWithLabelSelectors {
  namespace: string;
  /**
   * 默认 santorini.io/manageable=true
   */
  labelSelectors?: string[];
}

interface ObjectContainer {
  namespace?: string;
  yaml?: string;
}

export const kubeServiceApi = createApi({
  reducerPath: 'kubeService',
  baseQuery: kubeBaseApi,
  // tagTypes: ['kubernetesJWTToken'],
  endpoints: (build) => {
    return {
      createDeployment: build.mutation<undefined, ObjectContainer>({
        query: ({ namespace, yaml }) => ({
          url: `/apis/apps/v1/namespaces/${namespace}/deployments`,
          method: 'POST',
          body: yaml,
          headers: {
            'Content-Type': 'application/yaml',
          },
        }),
      }),
      deployments: build.query<
        KubeDeploymentProps[],
        NamespaceWithLabelSelectors,
        KubeDeploymentListProps
      >({
        transformResponse: (baseQueryReturnValue) => {
          return baseQueryReturnValue?.items ?? [];
        },
        query: ({ namespace, labelSelectors }) => ({
          url: `/apis/apps/v1/namespaces/${namespace}/deployments`,
          params: {
            labelSelector:
              labelSelectors?.join(',') ?? 'santorini.io/manageable=true',
          },
        }),
      }),
    };
  },
});

export const { useDeploymentsQuery, useCreateDeploymentMutation } =
  kubeServiceApi;
