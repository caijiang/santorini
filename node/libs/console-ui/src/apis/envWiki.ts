import { createApi } from '@reduxjs/toolkit/query/react';
import { stBaseQuery } from './api';
import { UserDataSimple } from './deployment';

export interface EnvWikiContent {
  envId: string;
  title: string;
  content: string;
  removed: boolean;
  global: boolean;
  /**
   * 创建或者更新时间
   */
  time: string;
  /**
   * 最近的维护者
   */
  operator: UserDataSimple;
}

export const envWikiApi = createApi({
  reducerPath: 'consoleEnvWikiApi',
  baseQuery: stBaseQuery,
  tagTypes: ['EnvWiki'],
  endpoints: (build) => {
    return {
      oneWiki: build.query<string, { env: string; title: string }>({
        providesTags: ['EnvWiki'],
        query: ({ env, title }) => ({
          responseHandler: 'text',
          url: `/publicEnvWikis/${env}/${encodeURIComponent(title)}`,
        }),
      }),
      //<editor-fold desc="关于 wiki的写入获取修改和删除">
      envWikis: build.query<EnvWikiContent[], string>({
        providesTags: ['EnvWiki'],
        query: (arg) => `/envs/${arg}/wikis`,
      }),
      createEnvWiki: build.mutation<
        undefined,
        { env: string; title: string; content: string; global: boolean }
      >({
        invalidatesTags: ['EnvWiki'],
        query: ({ env, title, content, global }) => ({
          url: `/envs/${env}/wikis`,
          method: 'POST',
          body: { title, content, global },
        }),
      }),
      editEnvWiki: build.mutation<
        undefined,
        { env: string; title: string; content: string; global: boolean }
      >({
        invalidatesTags: ['EnvWiki'],
        query: ({ env, title, content, global }) => ({
          url: `/envs/${env}/wikis/${encodeURIComponent(title)}`,
          method: 'PUT',
          body: { content, global, title },
        }),
      }),
      deleteEnvWiki: build.mutation<undefined, { env: string; title: string }>({
        invalidatesTags: ['EnvWiki'],
        query: ({ env, title }) => ({
          url: `/envs/${env}/wikis/${encodeURIComponent(title)}`,
          method: 'DELETE',
        }),
      }),
      //</editor-fold>
    };
  },
});

export const {
  useOneWikiQuery,
  useEnvWikisQuery,
  useCreateEnvWikiMutation,
  useEditEnvWikiMutation,
  useDeleteEnvWikiMutation,
} = envWikiApi;
