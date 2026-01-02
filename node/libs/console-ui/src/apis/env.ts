import { createApi } from '@reduxjs/toolkit/query/react';
import { IObjectMeta } from '@kubernetes-models/apimachinery/apis/meta/v1/ObjectMeta';
import { stBaseQuery } from './api';

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

interface EnvVar {
  name: string;
  value?: string;
  secret: boolean;
}

export const envApi = createApi({
  reducerPath: 'consoleEnvApi',
  baseQuery: stBaseQuery,
  tagTypes: ['Envs', 'Resources', 'ShareEnvVars'],
  endpoints: (build) => {
    return {
      //<editor-fold desc="共享环境变量">
      shareEnvVars: build.query<EnvVar[], string>({
        providesTags: ['ShareEnvVars'],
        query: (arg) => `/envs/${arg}/shareEnvs`,
      }),
      createEnvVar: build.mutation<undefined, { env: string; var: EnvVar }>({
        invalidatesTags: ['ShareEnvVars'],
        query: ({ env, var: { name, value, secret } }) => ({
          url: `/envs/${env}/shareEnvs/${name}`,
          method: 'PUT',
          params: secret ? { secret: 'true' } : undefined,
          body: value,
        }),
      }),
      deleteEnvVar: build.mutation<undefined, { env: string; var: EnvVar }>({
        invalidatesTags: ['ShareEnvVars'],
        query: ({ env, var: { name, secret } }) => ({
          url: `/envs/${env}/shareEnvs/${name}`,
          method: 'DELETE',
          params: secret ? { secret: 'true' } : undefined,
        }),
      }),
      //</editor-fold>
      dockerConfigJsonSecretNames: build.query<string[], string>({
        query: (arg) => `/dockerConfigJsonSecretNames/${arg}`,
      }),
      //<editor-fold desc="环境资源">
      resources: build.query<
        SantoriniResourceData[],
        { envId: string; params?: any }
      >({
        providesTags: ['Resources'],
        query: ({ envId, params }) => ({
          url: `/envs/${envId}/resources`,
          params,
        }),
      }),
      createResource: build.mutation<undefined, { envId: string; data: any }>({
        invalidatesTags: ['Resources'],
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
        invalidatesTags: ['Resources'],
        query: ({ envId, name }) => ({
          url: `/envs/${envId}/resources/${name}`,
          method: 'DELETE',
        }),
      }),
      //</editor-fold>
      //<editor-fold desc="环境以及属性的自身管理">
      updateEnv: build.mutation<undefined, any>({
        invalidatesTags: ['Envs'],
        query: (arg) => ({ url: '/envs', method: 'POST', body: arg }),
      }),
      // queryFn
      envs: build.query<CUEnv[] | undefined, IObjectMeta[] | undefined, Env[]>({
        providesTags: ['Envs'],
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
      //</editor-fold>
    };
  },
});

export const {
  useEnvsQuery,
  useUpdateEnvMutation,
  useResourcesQuery,
  useCreateResourceMutation,
  useDeleteResourceMutation,
  useDockerConfigJsonSecretNamesQuery,
  useShareEnvVarsQuery,
  useCreateEnvVarMutation,
  useDeleteEnvVarMutation,
} = envApi;
