import { createApi } from '@reduxjs/toolkit/query/react';
import { apiBase } from '@private-everest/app-support';
import { IObjectMeta } from '@kubernetes-models/apimachinery/apis/meta/v1/ObjectMeta';

// 应用中直接使用的 一律做 CU
export interface CUEnv {
  // kubeMeta: IObjectMeta;
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
          body: { description: null, ...data },
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
      envs: build.query<CUEnv[] | undefined, IObjectMeta[] | undefined, Env[]>({
        providesTags: ['env'],
        query: (arg) => ({
          url: arg
            ? `/envs/batch/${arg?.map((it) => it.name!!)?.join(',') ?? '99999'}`
            : '/envs',
          method: 'GET',
        }),
        // transformResponse(baseQueryReturnValue, __, arg) {
        //   if (!baseQueryReturnValue || !arg || arg.length == 0) {
        //     return undefined;
        //   }
        //   if (!_.isArrayLike(baseQueryReturnValue)) {
        //     return undefined;
        //   }
        //   return arg.map((meta) => {
        //     const server = baseQueryReturnValue.find(
        //       (it) => it.id == meta.name
        //     );
        //     if (!server) {
        //       return {
        //         // kubeMeta: meta,
        //         id: meta.name!!,
        //         name: meta.name!!,
        //         production: false,
        //       };
        //     }
        //     return {
        //       // kubeMeta: meta,
        //       id: meta.name!!,
        //       name: server.name,
        //       production: server.production,
        //     };
        //   });
        // },
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
} = envApi;
