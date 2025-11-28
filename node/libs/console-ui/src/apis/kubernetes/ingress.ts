import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi } from './kubernetes';
import { CUEnv } from '../env';
import { IIngress, IIngressList } from 'kubernetes-models/networking.k8s.io/v1';

export const ingressApi = createApi({
  reducerPath: 'kubeIngress',
  baseQuery: kubeBaseApi,
  // tagTypes: ['kubernetesJWTToken'],
  endpoints: (build) => {
    return {
      ingresses: build.query<IIngress[], CUEnv, IIngressList>({
        transformResponse(baseQueryReturnValue) {
          return baseQueryReturnValue.items.filter(
            (it) => it.spec?.rules?.length == 1 && it.spec?.rules!![0].host
          );
        },
        // providesTags: ['kubernetesJWTToken'],
        query: ({ id }) => ({
          url: `/apis/networking.k8s.io/v1/namespaces/${id}/ingresses`,
          params: {
            labelSelector: 'santorini.io/manageable=true',
          },
        }),
      }),
    };
  },
});

export const { useIngressesQuery } = ingressApi;
