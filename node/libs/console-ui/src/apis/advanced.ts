import { createApi } from '@reduxjs/toolkit/query/react';
import { stBaseQuery } from './api';
import _ from 'lodash';

export interface CustomMenu {
  name?: string;
  iconName?: string;
}

interface SystemStringData {
  name: string;
  value?: string;
}

export const advancedApi = createApi({
  reducerPath: 'consoleAdvancedApi',
  baseQuery: stBaseQuery,
  tagTypes: ['SystemString'],
  endpoints: (build) => {
    return {
      updateSystemString: build.mutation<undefined, SystemStringData>({
        invalidatesTags: ['SystemString'],
        query: ({ name, value }) => ({
          url: `/systemStrings/${name}`,
          method: 'PUT',
          body: value,
        }),
      }),
      createSystemString: build.mutation<undefined, SystemStringData>({
        invalidatesTags: ['SystemString'],
        query: (body) => ({
          url: `/systemStrings`,
          method: 'POST',
          body,
        }),
      }),
      deleteSystemString: build.mutation<undefined, string>({
        invalidatesTags: ['SystemString'],
        query: (arg) => ({
          url: `/systemStrings/${arg}`,
          method: 'DELETE',
        }),
      }),
      customMenus: build.query<CustomMenu[], undefined, string>({
        transformResponse(baseQueryReturnValue) {
          if (!baseQueryReturnValue) return [];
          if (!_.isString(baseQueryReturnValue)) return [];
          const obj = JSON.parse(baseQueryReturnValue);
          if (!_.isArrayLike(obj)) return [];
          return obj;
        },
        query: () => ({
          url: '/systemStrings/customMenu',
          responseHandler: 'text',
        }),
        providesTags: ['SystemString'],
      }),
    };
  },
});

export const {
  useCustomMenusQuery,
  useCreateSystemStringMutation,
  useUpdateSystemStringMutation,
  useDeleteSystemStringMutation,
} = advancedApi;
