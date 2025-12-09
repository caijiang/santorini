import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi } from './kubernetes';
import {
  ConfigMap,
  IConfigMap,
  IConfigMapList,
  INamespace,
  INamespaceList,
  ISecret,
  ISecretList,
  Namespace,
  Secret,
} from 'kubernetes-models/v1';
import { ModelData } from '@kubernetes-models/base';
import YAML from 'yaml';
import { NamespaceWithLabelSelectors, TypedObjectContainer } from './type';

export const commonApi = createApi({
  reducerPath: 'kubeCommon',
  baseQuery: kubeBaseApi,
  tagTypes: ['Secrets', 'ConfigMaps', 'Namespaces'],
  endpoints: (build) => {
    return {
      createConfigMap: build.mutation<
        undefined,
        TypedObjectContainer<IConfigMap>
      >({
        invalidatesTags: ['ConfigMaps'],
        query: ({ data, namespace }) => ({
          url: `/api/v1/namespaces/${namespace}/configmaps`,
          method: 'POST',
          body: YAML.stringify(new ConfigMap(data).toJSON()),
          headers: {
            'Content-Type': 'application/yaml',
          },
        }),
      }),
      configMaps: build.query<
        IConfigMap[],
        NamespaceWithLabelSelectors,
        IConfigMapList
      >({
        providesTags: ['ConfigMaps'],
        transformResponse(baseQueryReturnValue) {
          return baseQueryReturnValue.items;
        },
        query: ({ namespace, labelSelectors }) => ({
          url: namespace
            ? `/api/v1/namespaces/${namespace}/configmaps`
            : `/api/v1/configmaps`,
          params: {
            labelSelector:
              labelSelectors?.join(',') ?? 'santorini.io/manageable=true',
          },
        }),
      }),
      createSecrets: build.mutation<undefined, TypedObjectContainer<ISecret>>({
        invalidatesTags: ['Secrets'],
        query: ({ data, namespace }) => ({
          url: `/api/v1/namespaces/${namespace}/secrets`,
          method: 'POST',
          body: YAML.stringify(new Secret(data).toJSON()),
          headers: {
            'Content-Type': 'application/yaml',
          },
        }),
      }),
      secrets: build.query<ISecret[], NamespaceWithLabelSelectors, ISecretList>(
        {
          providesTags: ['Secrets'],
          transformResponse: (baseQueryReturnValue, _, { name }) => {
            if (name) {
              const x = baseQueryReturnValue as unknown as ISecret | undefined;
              return x ? [x] : [];
            }
            return baseQueryReturnValue?.items;
          },
          query: ({ namespace, name, labelSelectors }) => ({
            url: !name
              ? `/api/v1/namespaces/${namespace}/secrets`
              : `/api/v1/namespaces/${namespace}/secrets/${name}`,
            params: {
              labelSelector:
                labelSelectors?.join(',') ?? 'santorini.io/manageable=true',
            },
          }),
        }
      ),
      // 专门找 pullImageSecret 的
      secretByNamespace: build.query<ISecret[], string, ISecretList>({
        providesTags: ['Secrets'],
        transformResponse: (baseQueryReturnValue) => {
          return (
            baseQueryReturnValue?.items?.filter(
              (it) => !it?.metadata?.labels?.['santorini.io/resource-type']
            ) ?? []
          );
        },
        query: (arg) => ({
          url: `/api/v1/namespaces/${arg}/secrets`,
          params: {
            labelSelector: 'santorini.io/manageable=true',
          },
        }),
      }),
      createNamespace: build.mutation<undefined, ModelData<INamespace>>({
        invalidatesTags: ['Namespaces'],
        query: (arg) => ({
          url: '/api/v1/namespaces',
          method: 'POST',
          body: YAML.stringify(new Namespace(arg).toJSON()),
          headers: {
            'Content-Type': 'application/yaml',
          },
        }),
      }),
      namespaces: build.query<
        INamespace[],
        NamespaceWithLabelSelectors,
        INamespaceList
      >({
        providesTags: ['Namespaces'],
        transformResponse(baseQueryReturnValue) {
          return baseQueryReturnValue.items;
        },
        // providesTags: ['kubernetesJWTToken'],
        query: ({ labelSelectors }) => ({
          url: '/api/v1/namespaces',
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
  useNamespacesQuery,
  useSecretByNamespaceQuery,
  useConfigMapsQuery,
  useCreateNamespaceMutation,
  useCreateConfigMapMutation,
  useSecretsQuery,
  useCreateSecretsMutation,
} = commonApi;
