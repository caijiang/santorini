import { createApi } from '@reduxjs/toolkit/query/react';
import { apiBase } from '@private-everest/app-support';
import { kotlinEnumToString } from '@private-everest/kotlin-utils';
import { io } from '@santorini/generated-santorini-model';

export const userApi = createApi({
  reducerPath: 'consoleUserApi',
  baseQuery: apiBase,
  tagTypes: ['Envs', 'ServiceRoles'],
  endpoints: (build) => {
    return {
      userServiceRoles: build.query<
        Record<string, io.santorini.model.ServiceRole[]>,
        string
      >({
        providesTags: ['ServiceRoles'],
        query: (arg) => `/users/${arg}/services`,
      }),
      addServiceRoles: build.mutation<
        undefined,
        {
          userId: string;
          serviceId: string;
          role: io.santorini.model.ServiceRole;
        }
      >({
        invalidatesTags: ['ServiceRoles'],
        query: ({ userId, role, serviceId }) => ({
          url: `/users/${userId}/services`,
          method: 'POST',
          body: {
            first: serviceId,
            second: kotlinEnumToString(role),
          },
        }),
      }),
      removeServiceRoles: build.mutation<
        undefined,
        {
          userId: string;
          serviceId: string;
          role: io.santorini.model.ServiceRole;
        }
      >({
        invalidatesTags: ['ServiceRoles'],
        query: ({ userId, role, serviceId }) => ({
          url: `/users/${userId}/services/${serviceId}/${kotlinEnumToString(
            role
          )}`,
          method: 'DELETE',
        }),
      }),
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

export const {
  useUserEnvsQuery,
  useRemoveEnvMutation,
  useGrantEnvMutation,
  useUserServiceRolesQuery,
  useAddServiceRolesMutation,
  useRemoveServiceRolesMutation,
} = userApi;
