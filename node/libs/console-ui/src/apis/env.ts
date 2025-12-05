import { createApi } from '@reduxjs/toolkit/query/react';
import { apiBase } from '@private-everest/app-support';
import { ObjectMeta } from 'cdk8s-plus-33/lib/imports/k8s';
import _ from 'lodash';

// 应用中直接使用的 一律做 CU
export interface CUEnv {
  kubeMeta: ObjectMeta;
  id: string;
  name: string;
  production: boolean;
}

interface Env {
  id: string;
  name: string;
  production: boolean;
}

interface X {
  name: string;
  description?: string;
  properties: Record<string, string>;
}

// val type: ResourceType,
export interface SantoriniResourceData extends X {
  // type: io.santorini.model.ResourceType;
  type: string;
}

export const envApi = createApi({
  reducerPath: 'consoleEnvApi',
  baseQuery: apiBase,
  tagTypes: ['env', 'resources'],
  endpoints: (build) => {
    return {
      embedNacosServerAddr: build.query<string | undefined, undefined>({
        query: () => ({
          url: '/embedNacosServerAddr',
          responseHandler: 'text',
        }),
      }),
      resources: build.query<
        SantoriniResourceData[],
        { envId: string; params?: any }
      >({
        providesTags: ['resources'],
        query: ({ envId, params }) => ({
          url: `/envs/${envId}/resources`,
          params,
        }),
      }),
      createResource: build.mutation<undefined, { envId: string; data: any }>({
        invalidatesTags: ['resources'],
        query: ({ envId, data }) => ({
          url: `/envs/${envId}/resources`,
          method: 'POST',
          body: data,
        }),
      }),
      deleteResource: build.mutation<
        undefined,
        { envId: string; name: string }
      >({
        invalidatesTags: ['resources'],
        query: ({ envId, name }) => ({
          url: `/envs/${envId}/resources/${name}`,
          method: 'DELETE',
        }),
      }),
      updateEnv: build.mutation<undefined, any>({
        invalidatesTags: ['env'],
        query: (arg) => ({ url: '/envs', method: 'POST', body: arg }),
      }),
      // queryFn
      envs: build.query<CUEnv[] | undefined, ObjectMeta[] | undefined, Env[]>({
        providesTags: ['env'],
        query: (arg) => ({
          url: `/envs/batch/${
            arg?.map((it) => it.name!!)?.join(',') ?? '99999'
          }`,
          method: 'GET',
        }),
        transformResponse(baseQueryReturnValue, __, arg) {
          if (!baseQueryReturnValue || !arg || arg.length == 0) {
            return undefined;
          }
          if (!_.isArrayLike(baseQueryReturnValue)) {
            return undefined;
          }
          return arg.map((meta) => {
            const server = baseQueryReturnValue.find(
              (it) => it.id == meta.name
            );
            if (!server) {
              return {
                kubeMeta: meta,
                id: meta.name!!,
                name: meta.name!!,
                production: false,
              };
            }
            return {
              kubeMeta: meta,
              id: meta.name!!,
              name: server.name,
              production: server.production,
            };
          });
        },
      }),
    };
  },
});

export const {
  useEnvsQuery,
  useUpdateEnvMutation,
  useResourcesQuery,
  useCreateResourceMutation,
  useDeleteResourceMutation,
  useEmbedNacosServerAddrQuery,
} = envApi;
