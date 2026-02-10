import { createApi } from '@reduxjs/toolkit/query/react';
import { stBaseQuery } from './api';
import { PageResult } from '../common/ktor';
import { DeploymentDeployData, LastReleaseDeploymentSummary } from './service';

export interface PreDeployResult {
  /**
   * 不正常情况就给这个
   */
  warnMessage?: string;
  /**
   * 空的话就是不存在
   */
  imageDigest?: string;
  /**
   * 平台是否满足
   */
  imagePlatformMatch: boolean;
  /**
   * 成功部署的目标环境
   */
  successfulEnvs?: string[];
}

export interface UserDataSimple {
  id: string;
  name: string;
  avatarUrl: string;
  createTime: string;
}

interface DeploymentInList {
  env: string;
  operator: UserDataSimple;
  imageRepository: string;
  imageTag?: string;
  expiredVersion: boolean;
  /**
   * 是否完成/停止部署
   */
  completed: boolean;
  /**
   * 是否成功部署
   */
  successful: boolean;
  createTime: string;
}

export const deploymentApi = createApi({
  reducerPath: 'consoleDeploymentApi',
  baseQuery: stBaseQuery,
  tagTypes: ['LastRelease', 'DeployHistory'],
  endpoints: (build) => {
    return {
      /**
       * 获取近期发布记录
       */
      deploymentHistorySummary: build.query<
        DeploymentInList[],
        {
          serviceId?: string;
          envId?: string;
          limit?: number;
        },
        PageResult<DeploymentInList>
      >({
        providesTags: (_, __, { serviceId }) => {
          if (!serviceId) return [];
          return [{ type: 'DeployHistory', id: serviceId }];
        },
        transformResponse(baseQueryReturnValue) {
          return baseQueryReturnValue.records;
        },
        query: ({ limit, ...args }) => ({
          url: '/deployments',
          params: {
            ...args,
            limit: `${limit ?? 10}`,
          },
        }),
      }),
      preDeploy: build.mutation<
        PreDeployResult,
        { envId: string; serviceId: string; data: DeploymentDeployData }
      >({
        query: ({ envId, serviceId, data }) => ({
          method: 'POST',
          url: `/deployments/preDeploy/${envId}/${serviceId}`,
          body: data,
          // application/json 而且带有括号
        }),
      }),
      deploy: build.mutation<
        string,
        { envId: string; serviceId: string; data: DeploymentDeployData }
      >({
        invalidatesTags: (_, __, { envId, serviceId }) => {
          return [
            { type: 'DeployHistory', id: serviceId },
            { type: 'LastRelease', id: `${envId}-${serviceId}` },
          ];
        },
        query: ({ envId, serviceId, data }) => ({
          method: 'POST',
          url: `/deployments/deploy/${envId}/${serviceId}`,
          body: data,
        }),
      }),
      /**
       * 回收一次失败的部署
       */
      invokeDeploy: build.mutation<
        undefined,
        { envId: string; serviceId: string; data: string }
      >({
        invalidatesTags: (_, __, { envId, serviceId }) => {
          return [
            { type: 'DeployHistory', id: serviceId },
            { type: 'LastRelease', id: `${envId}-${serviceId}` },
          ];
        },
        query: (arg) => ({
          method: 'DELETE',
          url: `/deployments/${arg.data}`,
        }),
      }),
      /**
       * 汇报结果
       */
      reportDeployResult: build.mutation<
        undefined,
        { id: string; generation: string }
      >({
        query: ({ id, generation }) => ({
          method: 'PUT',
          url: `/deployments/${id}/targetGeneration`,
          body: generation,
          headers: {
            'Content-Type': 'text/plain',
          },
        }),
      }),
      /**
       * 获取最后一次发布的记录
       */
      lastRelease: build.query<
        LastReleaseDeploymentSummary | undefined,
        {
          serviceId: string;
          envId: string;
        }
      >({
        providesTags: (_, __, { envId, serviceId }) => {
          return [{ type: 'LastRelease', id: `${envId}-${serviceId}` }];
        },
        query: ({ serviceId, envId }) =>
          `/services/${serviceId}/lastRelease/${envId}`,
      }),
    };
  },
});

export const {
  useDeploymentHistorySummaryQuery,
  useLastReleaseQuery,
  usePreDeployMutation,
} = deploymentApi;
