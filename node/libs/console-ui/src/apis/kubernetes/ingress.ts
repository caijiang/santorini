import { createApi } from '@reduxjs/toolkit/query/react';
import { kubeBaseApi } from './kubernetes';
import { CUEnv } from '../env';
import { IIngress, IIngressList } from 'kubernetes-models/networking.k8s.io/v1';
import { ObjectContainer } from './service';

export const ingressApi = createApi({
  reducerPath: 'kubeIngress',
  baseQuery: kubeBaseApi,
  tagTypes: ['Ingresses'],
  endpoints: (build) => {
    return {
      createIngress: build.mutation<undefined, ObjectContainer>({
        invalidatesTags: ['Ingresses'],
        query: ({ namespace, yaml }) => ({
          url: `/apis/networking.k8s.io/v1/namespaces/${namespace}/ingresses`,
          method: 'POST',
          body: yaml,
          headers: {
            'Content-Type': 'application/yaml',
          },
        }),
      }),
      removeIngress: build.mutation<undefined, ObjectContainer>({
        invalidatesTags: ['Ingresses'],
        query: ({ namespace, name }) => ({
          url: `/apis/networking.k8s.io/v1/namespaces/${namespace}/ingresses/${name}`,
          method: 'DELETE',
        }),
      }),
      editIngress: build.mutation<undefined, ObjectContainer>({
        invalidatesTags: ['Ingresses'],
        query: ({ namespace, yaml, name }) => ({
          url: `/apis/networking.k8s.io/v1/namespaces/${namespace}/ingresses/${name}`,
          method: 'PUT',
          body: yaml,
          headers: {
            'Content-Type': 'application/yaml',
          },
        }),
      }),
      ingresses: build.query<IIngress[], CUEnv, IIngressList>({
        transformResponse(baseQueryReturnValue) {
          return baseQueryReturnValue.items;
          //   .filter(
          //   (it) => it.spec?.rules?.length >0 && it.spec?.rules!![0].host
          // )
        },
        providesTags: ['Ingresses'],
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

export const {
  useIngressesQuery,
  useCreateIngressMutation,
  useEditIngressMutation,
  useRemoveIngressMutation,
} = ingressApi;
