import {
  BaseQueryFn,
  EndpointBuilder,
  RetryOptions,
} from '@reduxjs/toolkit/query';
import { FetchBaseQueryError } from '@reduxjs/toolkit/query/react';
import {
  NamespacedNamedResource,
  ObjectContainer,
  PatchObjectContainer,
} from './type';
import YAML from 'yaml';

function toId({ namespace, name }: NamespacedNamedResource) {
  return `${namespace}-${name}`;
}

/**
 * 负责创建用于 redux-toolkit 的 api，它是可以更好的完成 crud的功能
 * @param kindName 资源名称,也是缓存 tag;建议大写开头
 * @param version kubectl api-resources 可获取,比如 apps/v1 或者 v1
 * @param build rqk
 */
export function createCrudApisForNamespacedResources<
  ResourceItem,
  NAME extends string
>(
  kindName: NAME,
  version: string,
  build: EndpointBuilder<
    BaseQueryFn<any, unknown, FetchBaseQueryError, RetryOptions, {}>,
    NAME | string,
    string
  >
) {
  const toKindResourceUrl = ({ namespace }: NamespacedNamedResource) => {
    if (version.includes('/')) {
      return `/apis/${version}/namespaces/${namespace}/${kindName.toLowerCase()}`;
    }
    return `/api/${version}/namespaces/${namespace}/${kindName.toLowerCase()}`;
  };
  const toNamedResourceUrl = (resource: NamespacedNamedResource) => {
    return `${toKindResourceUrl(resource)}/${resource.name}`;
  };
  // list
  // /apis/apps/v1/namespaces/
  const getApi = build.query<ResourceItem, NamespacedNamedResource>({
    providesTags: (_, __, arg) => [{ type: kindName, id: toId(arg) }],
    query: (resource) => ({
      url: toNamedResourceUrl(resource),
      params: {
        labelSelector:
          resource.labelSelectors?.join(',') ?? 'santorini.io/manageable=true',
      },
    }),
  });
  const createApi = build.mutation<undefined, ObjectContainer>({
    query: (resource) => ({
      url: toKindResourceUrl(resource),
      params: {
        fieldManager: 'santorini',
      },
      method: 'POST',
      body: YAML.stringify(resource.jsonObject),
      headers: {
        'Content-Type': 'application/yaml',
      },
    }),
  });
  // https://kubernetes.io/zh-cn/docs/reference/using-api/server-side-apply/#conflicts
  // 给用户选择 强制推送(force=true),忽略该字段(让渡管理权),合并字段(使用该字段)
  const patchApi = build.mutation<ResourceItem, PatchObjectContainer>({
    invalidatesTags: (_, __, arg) => [{ type: kindName, id: toId(arg) }],
    query: (resource) => ({
      url: toNamedResourceUrl(resource),
      params: {
        fieldManager: 'santorini',
        force: resource.force ? 'true' : undefined,
      },
      method: 'PATCH',
      body: YAML.stringify(resource.jsonObject),
      headers: {
        'Content-Type': 'application/apply-patch+yaml',
      },
    }),
  });
  const updateApi = build.mutation<undefined, ObjectContainer>({
    invalidatesTags: (_, __, arg) => [{ type: kindName, id: toId(arg) }],
    query: (resource) => ({
      url: toNamedResourceUrl(resource),
      params: {
        fieldManager: 'santorini',
      },
      method: 'PUT',
      body: YAML.stringify(resource.jsonObject),
      headers: {
        'Content-Type': 'application/yaml',
      },
    }),
  });
  const deleteApi = build.mutation<undefined, NamespacedNamedResource>({
    invalidatesTags: (_, __, arg) => [{ type: kindName, id: toId(arg) }],
    query: (resource) => ({
      url: toNamedResourceUrl(resource),
      method: 'DELETE',
    }),
  });

  return {
    [`create${kindName}`]: createApi,
    [`get${kindName}`]: getApi,
    [`patch${kindName}`]: patchApi,
    [`update${kindName}`]: updateApi,
    [`delete${kindName}`]: deleteApi,
  } as { [K in `create${typeof kindName}`]: typeof createApi } & {
    [K in `get${typeof kindName}`]: typeof getApi;
  } & {
    [K in `patch${typeof kindName}`]: typeof patchApi;
  } & {
    [K in `update${typeof kindName}`]: typeof updateApi;
  } & {
    [K in `delete${typeof kindName}`]: typeof deleteApi;
  };
}
