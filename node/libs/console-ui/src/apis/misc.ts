import { createApi } from '@reduxjs/toolkit/query/react';
import { stBaseQuery } from './api';
import { ClusterResourceStat } from '../components/kubernetes/resources/types';

export const miscApi = createApi({
  reducerPath: 'consoleMiscApi',
  baseQuery: stBaseQuery,
  endpoints: (build) => {
    return {
      clusterResourceStat: build.query<ClusterResourceStat, undefined>({
        query: () => '/clusterResourceStat',
      }),
      appName: build.query<string, undefined>({
        query: () => ({ url: '/appName', responseHandler: 'text' }),
      }),
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

export const {
  useEmbedNacosServerAddrQuery,
  useDashboardHostQuery,
  useAppNameQuery,
  useClusterResourceStatQuery,
} = miscApi;
