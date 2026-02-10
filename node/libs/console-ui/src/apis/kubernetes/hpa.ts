import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi } from './kubernetes';
import { HorizontalPodAutoscaler } from 'kubernetes-models/autoscaling/v2';
import { createCrudApisForNamespacedResources } from './crud';

export const kubeHpaApi = createApi({
  reducerPath: 'kubeHpa',
  baseQuery: kubeBaseApi,
  tagTypes: ['Hpa', 'HorizontalPodAutoScalers'],
  endpoints: (build) => {
    return {
      ...createCrudApisForNamespacedResources<
        HorizontalPodAutoscaler,
        'HorizontalPodAutoScalers'
      >('HorizontalPodAutoScalers', 'autoscaling/v2', build),
    };
  },
});

export const {
  useGetHorizontalPodAutoScalersQuery,
  useDeleteHorizontalPodAutoScalersMutation,
  useCreateHorizontalPodAutoScalersMutation,
  useUpdateHorizontalPodAutoScalersMutation,
} = kubeHpaApi;
