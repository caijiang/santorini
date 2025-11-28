import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi } from './kubernetes';
import { IDeploymentList } from 'kubernetes-models/apps/v1/DeploymentList';
import { IDeployment } from 'kubernetes-models/apps/v1/Deployment';
import { IService } from 'kubernetes-models/v1';

interface NamespaceWithLabelSelectors {
  namespace?: string;
  /**
   * 默认 santorini.io/manageable=true
   */
  labelSelectors?: string[];
}

interface NamespacedNamedResource {
  namespace?: string;
  name?: string;
}

interface ObjectContainer {
  namespace?: string;
  yaml?: string;
}

export const kubeServiceApi = createApi({
  reducerPath: 'kubeService',
  baseQuery: kubeBaseApi,
  tagTypes: ['deployments', 'services'],
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
