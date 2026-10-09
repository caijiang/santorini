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
      /**
       * 页面顶部说明用的只读信息：同步范围 + 证书去向。
       *
       * 没有 tag：这些值来自服务端环境变量（`EASY_DOMAIN_*`），运行期不会变，
       * 只有重启才会变——没有能让它失效的事件可言。
       */
      syncInfo: build.query<DomainSyncInfo, undefined>({
        query: () => '/domains/syncInfo',
      }),
      createDomain: build.mutation<undefined, string>({
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

/**
 * 服务端 `DomainSyncInfo` 的镜像。
 *
 * 有意不含阿里云 AK/SK：凭据是否配置只体现为 [certSyncEnabled]。
 * 三个可空字段在服务端未配置时就是 `null`，用 `?` 兜住。
 */
export interface DomainSyncInfo {
  /** A 记录允许的 IP 段（CIDR） */
  dnsCidrs: string[];
  /** CNAME 允许的目标后缀；与 `dnsCidrs` 是「或」关系 */
  dnsCnameSuffixes: string[];
  /** 证书最终落脚的地域 */
  aliyunRegion?: string | null;
  /** 证书最终挂载的 ALB 监听 */
  aliyunAlbListenerId?: string | null;
  /** 证书同步是否已完整配置（AK/SK + region + listener 缺一不可） */
  certSyncEnabled: boolean;
  /** 负载均衡 ID */
  aliyunLoadBalancerId?: string | null;
}

export const {
  useDomainsQuery,
  useSyncInfoQuery,
  useCreateDomainMutation,
  useDeleteDomainMutation,
} = easyDomainApi;
