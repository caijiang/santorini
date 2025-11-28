import { createApi } from '@reduxjs/toolkit/query/react';
import { apiBase } from '@private-everest/app-support';

export const hostApi = createApi({
  reducerPath: 'consoleHostApi',
  baseQuery: apiBase,
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
    };
  },
});

export interface HostSummary {
  hostname: string;
  issuerName?: string;
  secretName?: string;
}

export const { useSyncHostMutation } = hostApi;
