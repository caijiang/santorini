import { createApi } from '@reduxjs/toolkit/query/react';
import { apiBase } from '@private-everest/app-support';

export const userApi = createApi({
  reducerPath: 'consoleUserApi',
  baseQuery: apiBase,
  tagTypes: ['Envs'],
  endpoints: (build) => {
    return {
      userEnvs: build.query<string[], string>({
        providesTags: ['Envs'],
        query: (arg) => `/users/${arg}/envs`,
      }),
      grantEnv: build.mutation<
        undefined,
        {
          userId: string;
          envId: string;
        }
      >({
        invalidatesTags: ['Envs'],
        query: ({ userId, envId }) => ({
          url: `/users/${userId}/envs`,
          method: 'POST',
          body: envId,
        }),
      }),
      removeEnv: build.mutation<
        undefined,
        {
          userId: string;
          envId: string;
        }
      >({
        invalidatesTags: ['Envs'],
        query: ({ userId, envId }) => ({
          url: `/users/${userId}/envs/${envId}`,
          method: 'DELETE',
        }),
      }),
    };
  },
});

export const { useUserEnvsQuery, useRemoveEnvMutation, useGrantEnvMutation } =
  userApi;
