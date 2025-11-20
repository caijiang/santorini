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

export const envApi = createApi({
  reducerPath: 'consoleEnvApi',
  baseQuery: apiBase,
  tagTypes: ['env'],
  endpoints: (build) => {
    return {
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

export const { useEnvsQuery, useUpdateEnvMutation } = envApi;
