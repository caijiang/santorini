import { createApi } from '@reduxjs/toolkit/query/react';
import { apiBase } from '@private-everest/app-support';

type ServiceType = 'JVM';

export interface ResourceRequirement {
  type: string;
  name?: string;
}

export function keyOfResourceRequirement(rr: ResourceRequirement) {
  return rr.type + '-' + (rr.name ?? '');
}

export function nameOfResourceRequirement({ name, type }: ResourceRequirement) {
  if (!name) return type + '资源';
  return `${type}资源(${name})`;
}

export function generateDemoService(): ServiceConfigData {
  const rand = Math.ceil(Math.random() * 1000);
  return {
    id: `demo-service-${rand}`,
    name: '样例服务',
    type: 'JVM',
    resources: {
      cpu: {
        requestMillis: 100,
        limitMillis: 500,
      },
      memory: {
        requestMiB: 32,
        limitMiB: 64,
      },
    },
    ports: [
      {
        number: 80,
        name: 'http',
      },
    ],
  };
}

interface IHTTPGetAction {
  port: number;
  path: string;
  host?: string;
  scheme?: 'HTTP' | 'HTTPS';
}

interface IProbe {
  httpGet?: IHTTPGetAction;
  initialDelaySeconds: number;
  periodSeconds?: number;
  timeoutSeconds?: number;
  failureThreshold?: number;
  successThreshold?: number;
}

interface ILifecycle {
  terminationGracePeriodSeconds?: number;
  livenessProbe?: IProbe;
  readinessProbe?: IProbe;
  startupProbe?: IProbe;
}

export interface ServiceConfigData {
  /**
   * https://kubernetes.io/zh-cn/docs/concepts/overview/working-with-objects/names/#dns-label-names
   */
  id: string;
  /**
   * 50
   */
  name: string;
  type: ServiceType;
  /**
   * cpu: request: 1000 millis limit: 1500 millis memory: request: 512 mebibytes limit: 2048 mebibytes
   */
  resources: {
    /**
     * https://kubernetes.io/zh-cn/docs/concepts/configuration/manage-resources-containers/#meaning-of-cpu
     */
    cpu: {
      requestMillis: number;
      limitMillis: number;
    };
    /**
     * https://kubernetes.io/zh-cn/docs/concepts/configuration/manage-resources-containers/#meaning-of-memory
     */
    memory: {
      requestMiB: number;
      limitMiB: number;
    };
  };
  // image: string;
  ports: {
    number: number;
    /**
     * 这必须是 IANA_SVC_NAME,而且服务唯一
     */
    name: string;
  }[];
  // 声明依赖资源 先跳过
  requirements?: ResourceRequirement[];
  lifecycle?: ILifecycle;
}

export interface DeploymentDeployData {
  imageRepository: string;
  imageTag?: string;
  pullSecretName?: string[];
  resourcesSupply?: Record<string, string>;
}

export interface LastReleaseDeploymentSummary extends DeploymentDeployData {}

export const serviceApi = createApi({
  reducerPath: 'serviceApi',
  baseQuery: apiBase,
  tagTypes: ['Services', 'Deployments'],
  endpoints: (build) => {
    return {
      createService: build.mutation<undefined, ServiceConfigData>({
        invalidatesTags: ['Services'],
        query: (body) => ({
          url: '/services',
          body: {
            requirements: [],
            ...body,
          },
          method: 'POST',
        }),
      }),
      allService: build.query<ServiceConfigData[], undefined>({
        providesTags: ['Services'],
        query: () => `/services`,
      }),
      serviceById: build.query<ServiceConfigData | undefined, string>({
        providesTags: ['Services'],
        query: (arg) => `/services/${arg}`,
      }),
      updateService: build.mutation<
        undefined,
        { id: string; data: ServiceConfigData }
      >({
        invalidatesTags: ['Services'],
        query: ({ id, data }) => ({
          url: `/services/${id}`,
          body: {
            requirements: [],
            ...data,
          },
          method: 'PUT',
        }),
      }),
      deploy: build.mutation<
        undefined,
        { envId: string; serviceId: string; data: DeploymentDeployData }
      >({
        invalidatesTags: ['Deployments'],
        query: ({ envId, serviceId, data }) => ({
          method: 'POST',
          url: `/deployments/deploy/${envId}/${serviceId}`,
          body: data,
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
        providesTags: ['Deployments'],
        query: ({ serviceId, envId }) =>
          `/services/${serviceId}/lastRelease/${envId}`,
      }),
    };
  },
});

export const {
  useServiceByIdQuery,
  useCreateServiceMutation,
  useLastReleaseQuery,
  useAllServiceQuery,
  useUpdateServiceMutation,
} = serviceApi;
