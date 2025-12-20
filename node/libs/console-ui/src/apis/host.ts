import { createApi } from '@reduxjs/toolkit/query/react';
import { stBaseQuery } from './api';

export const hostApi = createApi({
  reducerPath: 'consoleHostApi',
  baseQuery: stBaseQuery,
  tagTypes: ['Hosts'],
  endpoints: (build) => {
    return {
      /**
       * 同步 host
       */
      syncHost: build.mutation<number, HostSummary[]>({
        query: (arg) => ({ url: '/hosts/sync', method: 'POST', body: arg }),
        onQueryStarted: async (_, { dispatch, queryFulfilled }) => {
          const { data } = await queryFulfilled;
          if (data > 0) {
            dispatch(hostApi.util.invalidateTags(['Hosts']));
          }
        },
      }),
      hosts: build.query<HostSummary[], undefined>({
        providesTags: ['Hosts'],
        query: () => '/hosts',
      }),
      createHost: build.mutation({
        invalidatesTags: ['Hosts'],
        query: (arg) => ({
          url: '/hosts',
          method: 'POST',
          body: arg,
        }),
      }),
    };
  },
});

export interface HostSummary {
  hostname: string;
  issuerName?: string;
  secretName?: string;
}

export const { useSyncHostMutation, useHostsQuery, useCreateHostMutation } =
  hostApi;
