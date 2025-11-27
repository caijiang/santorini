import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi } from './kubernetes';
import { IDeploymentList } from 'kubernetes-models/apps/v1/DeploymentList';
import { IDeployment } from 'kubernetes-models/apps/v1/Deployment';

interface NamespaceWithLabelSelectors {
  namespace?: string;
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
  tagTypes: ['deployments'],
  endpoints: (build) => {
    return {
      updateDeployment: build.mutation<
        undefined,
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
      createDeployment: build.mutation<undefined, ObjectContainer>({
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

export const { useDeploymentsQuery } = kubeServiceApi;
