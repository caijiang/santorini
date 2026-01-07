import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi } from './kubernetes';
import { HorizontalPodAutoscaler } from 'kubernetes-models/autoscaling/v2';

type HpaCoordinate = {
  envId: string;
  serviceId: string;
};

export const kubeHpaApi = createApi({
  reducerPath: 'kubeHpa',
  baseQuery: kubeBaseApi,
  tagTypes: ['Hpa'],
  endpoints: (build) => {
    return {
      //
      hpa: build.query<HorizontalPodAutoscaler | undefined, HpaCoordinate>({
        providesTags: ['Hpa'],
        query: ({ envId, serviceId }) => ({
          url: `/apis/autoscaling/v2/namespaces/${envId}/horizontalpodautoscalers/${serviceId}`,
        }),
      }),
      deleteHpa: build.mutation<undefined, HpaCoordinate>({
        invalidatesTags: ['Hpa'],
        query: ({ envId, serviceId }) => ({
          method: 'DELETE',
          url: `/apis/autoscaling/v2/namespaces/${envId}/horizontalpodautoscalers/${serviceId}`,
        }),
      }),
      createHpa: build.mutation<
        undefined,
        HpaCoordinate & { body: HorizontalPodAutoscaler }
      >({
        invalidatesTags: ['Hpa'],
        query: ({ envId, body }) => ({
          method: 'POST',
          url: `/apis/autoscaling/v2/namespaces/${envId}/horizontalpodautoscalers`,
          body,
        }),
      }),
      editHpa: build.mutation<
        undefined,
        HpaCoordinate & { body: HorizontalPodAutoscaler }
      >({
        invalidatesTags: ['Hpa'],
        query: ({ envId, serviceId, body }) => ({
          method: 'PUT',
          url: `/apis/autoscaling/v2/namespaces/${envId}/horizontalpodautoscalers/${serviceId}`,
          body,
        }),
      }),
    };
  },
});

export const {
  useHpaQuery,
  useDeleteHpaMutation,
  useCreateHpaMutation,
  useEditHpaMutation,
} = kubeHpaApi;
