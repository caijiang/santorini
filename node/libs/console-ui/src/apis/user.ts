import { createApi } from '@reduxjs/toolkit/query/react';
import { kotlinEnumToString } from '@private-everest/kotlin-utils';
import { io } from '@santorini/generated-santorini-model';
import { stBaseQuery } from './api';

export const userApi = createApi({
  reducerPath: 'consoleUserApi',
  baseQuery: stBaseQuery,
  tagTypes: ['Envs', 'ServiceRoles'],
  endpoints: (build) => {
    return {
      //<editor-fold desc="编辑用户的系统权限">
      updateGrantedAuthority: build.mutation<
        undefined,
        {
          userId: string;
          target: string;
          targetValue: boolean;
        }
      >({
        query: ({ userId, target, targetValue }) => ({
          url: `/users/${userId}/grantAuthorities/${target}`,
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
          },
          body: targetValue,
        }),
      }),
      //</editor-fold>
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
  useUpdateGrantedAuthorityMutation,
} = userApi;
