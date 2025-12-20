import { createApi } from '@reduxjs/toolkit/query/react';
import { stBaseQuery } from './api';

export const miscApi = createApi({
  reducerPath: 'consoleMiscApi',
  baseQuery: stBaseQuery,
  endpoints: (build) => {
    return {
      dashboardHost: build.query<string | undefined, undefined>({
        query: () => ({
          url: '/dashboardHost',
          responseHandler: 'text',
        }),
      }),
      embedNacosServerAddr: build.query<string | undefined, undefined>({
        query: () => ({
          url: '/embedNacosServerAddr',
          responseHandler: 'text',
        }),
      }),
    };
  },
});

export const { useEmbedNacosServerAddrQuery, useDashboardHostQuery } = miscApi;
