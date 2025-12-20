import { createApi } from '@reduxjs/toolkit/query/react';
import { stBaseQuery } from './api';

export const tokenApi = createApi({
  reducerPath: 'consoleTokenApi',
  baseQuery: stBaseQuery,
  tagTypes: ['kubernetesJWTToken'],
  endpoints: (build) => {
    return {
      // queryFn
      kubernetesJWTToken: build.query<string, undefined>({
        providesTags: ['kubernetesJWTToken'],
        query: () => ({
          responseHandler: 'text',
          url: '/token',
          method: 'POST',
        }),
      }),
    };
  },
});

export const {
  useKubernetesJWTTokenQuery,
} = tokenApi;
