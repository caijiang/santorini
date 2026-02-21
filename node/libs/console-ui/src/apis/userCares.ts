import { createApi } from '@reduxjs/toolkit/query/react';
import { stBaseQuery } from './api';

interface UserCareServiceMetaResource {
  serviceId: string;
  envId: string;
}

export const userCaresApi = createApi({
  reducerPath: 'consoleUserCaresApi',
  baseQuery: stBaseQuery,
  tagTypes: ['UserCareServices'],
  endpoints: (build) => {
    return {
      queryServiceCare: build.query<boolean, UserCareServiceMetaResource>({
        providesTags: (_, __, { envId, serviceId }) => [
          {
            type: 'UserCareServices',
            id: `${envId}-${serviceId}`,
          },
        ],
        query: ({ envId, serviceId }) => `/cares/${envId}/${serviceId}`,
      }),
      deleteServiceCare: build.mutation<undefined, UserCareServiceMetaResource>(
        {
          invalidatesTags: (_, __, { envId, serviceId }) => [
            {
              type: 'UserCareServices',
              id: `${envId}-${serviceId}`,
            },
          ],
          query: ({ envId, serviceId }) => ({
            url: `/cares/${envId}/${serviceId}`,
            method: 'DELETE',
          }),
        }
      ),
      takeServiceCare: build.mutation<undefined, UserCareServiceMetaResource>({
        invalidatesTags: (_, __, { envId, serviceId }) => [
          {
            type: 'UserCareServices',
            id: `${envId}-${serviceId}`,
          },
        ],
        query: ({ envId, serviceId }) => ({
          url: `/cares/${envId}/${serviceId}`,
          method: 'POST',
        }),
      }),
    };
  },
});

export const {
  useDeleteServiceCareMutation,
  useTakeServiceCareMutation,
  useQueryServiceCareQuery,
} = userCaresApi;
