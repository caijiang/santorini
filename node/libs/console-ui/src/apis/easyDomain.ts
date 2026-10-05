import { createApi } from '@reduxjs/toolkit/query/react';
import { stBaseQuery } from './api';

/**
 * easy-domain 的域名管理。
 *
 * 对应后端 `EasyDomainRoutes.kt` 挂的 `/domains`：对外契约只有 host 字符串 ——
 * Ingress 由服务端按模板渲染，namespace 由服务端 `EASY_DOMAIN_NAMESPACE` 决定，
 * 前端既不传 namespace 也不拼 YAML。
 *
 * 注意证书同步不在这里：它是宿主的调度任务驱动的（见 `JobRunner.heart`），
 * 与「用户增删域名」没有因果关系。
 */
export const easyDomainApi = createApi({
  reducerPath: 'consoleEasyDomainApi',
  baseQuery: stBaseQuery,
  tagTypes: ['Domains'],
  endpoints: (build) => {
    return {
      domains: build.query<string[], undefined>({
        providesTags: ['Domains'],
        query: () => '/domains',
      }),
      createDomain: build.mutation<string, string>({
        invalidatesTags: ['Domains'],
        query: (domain) => ({
          // 合法域名（字母数字、-、.、*.）本来就无需转义，这里是防御性的：
          // 万一以后放宽了服务端校验，也不会因为特殊字符拼坏路径。
          url: `/domains/${encodeURIComponent(domain)}`,
          method: 'POST',
        }),
      }),
      deleteDomain: build.mutation<undefined, string>({
        invalidatesTags: ['Domains'],
        query: (domain) => ({
          url: `/domains/${encodeURIComponent(domain)}`,
          method: 'DELETE',
        }),
      }),
    };
  },
});

export const {
  useDomainsQuery,
  useCreateDomainMutation,
  useDeleteDomainMutation,
} = easyDomainApi;
