import {
  fetchBaseQuery,
  FetchBaseQueryError,
  QueryReturnValue,
  retry,
} from '@reduxjs/toolkit/query/react';
import { tokenApi } from '../token';
import { BaseQueryApi } from '@reduxjs/toolkit/query';
import { IListMeta } from '@kubernetes-models/apimachinery/apis/meta/v1/ListMeta';

// 调用 kubernetes 的 api
// 有几个特点，都依赖 useKubernetesJWTTokenQuery 但是也有其他入参
// 都是 /kubernetes 打头，所以有一个 base

export const kubeBaseApi = retry(
  async (apiArg, api, extraOptions) => {
    // 从缓存读取 token，而不触发请求
    api.dispatch(tokenApi.endpoints.kubernetesJWTToken.initiate(undefined));
    const jwtToken = tokenApi.endpoints.kubernetesJWTToken.select(undefined)(
      // @ts-ignore
      api.getState()
    ).data;
    console.debug('jwtToken:', jwtToken);
    if (!jwtToken) {
      // throw new Error('please retry.');
      console.info('jwtToken not ready,take rest to retry.');
      await new Promise((resolve) => {
        setTimeout(resolve, 3500);
      });
      return {
        data: undefined as unknown,
        error: {} as unknown as undefined,
        isUninitialized: false,
        isLoading: false,
        isFetching: false,
        isSuccess: false,
        isError: true,
        refetch: () => {},
      };
    }
    const baseApi = fetchBaseQuery({
      baseUrl: '/kubernetes',
      prepareHeaders: (headers) => {
        headers.set('Authorization', 'Bearer ' + jwtToken);
        return headers;
      },
    });
    const result = await baseApi(apiArg, api, extraOptions);
    if (result.error && result.error.status == 404) {
      return {
        data: undefined,
      };
    }
    return result;
  },
  {
    //  暂定 1, 回头提供 api 允许定制
    maxRetries: 2,
  }
);
// type KuberBaseQueryType = typeof kubeBaseApi;

// useExampleQueryQuery(undefined, {});
//
// useAbc(useExampleQueryQuery);

// function useAbc<ResultType, QueryArg>(
//   inputQuery: TypedUseQuery<ResultType, QueryArg, KuberBaseQueryType>
// ): TypedUseQuery<ResultType, QueryArg, KuberBaseQueryType> {
//   const [arg, setArg] = useState<QueryArg>();
//   const [options, setOptions] = useState();
//   const { data: token } = useKubernetesJWTTokenQuery(undefined);
//   // @ts-ignore
//   const result = inputQuery(arg, {
//     skip: true,
//   });
//
//   // @ts-ignore
//   return (a0, a1) => {
//     // @ts-ignore
//     setArg(a0);
//     // @ts-ignore
//     setOptions(a1);
//     return result;
//   };
// }

// function useKubeApi()

interface QueryParameters {
  url: string;
  params: Record<string, string>;
}

interface ElementList<E> {
  items: Array<E>;
  metadata: IListMeta;
}

type MaybePromise<T> = T | PromiseLike<T>;

/**
 * 工具方法，可以获取分页获取所有对象
 * @param argsFunction 生成参数的函数
 * @param _api
 * @param _extraOptions
 * @param baseQuery
 */
export async function queryFunctionForList<Element>(
  argsFunction: () => QueryParameters,
  _api: BaseQueryApi,
  _extraOptions: {},
  baseQuery: (
    arg: any
  ) => MaybePromise<QueryReturnValue<unknown, FetchBaseQueryError, {}>>,
) {
  const allRecords: Element[] = [];
  let continueToken: string | undefined;
  let resourceVersion: string | undefined;

  try {
    // 循环获取所有分页
    do {
      // 构建查询参数
      const qp = argsFunction();
      const {params} = qp;
      params['limit'] = '500';
      if (continueToken) {
        params.continue = continueToken;
      }

      // params.labelSelector = toPodsLabelSelectors(args);

      // 构建 URL
      // const url = !namespace
      //   ? '/api/v1/pods'
      //   : `/api/v1/namespaces/${namespace}/pods`;

      // 执行查询
      const result = await baseQuery(qp);

      if (result.error) {
        return {error: result.error};
      }

      const response = result.data as ElementList<Element>;

      // 收集当前页的数据
      if (response.items) {
        allRecords.push(...response.items);
      }

      // 更新继续令牌
      continueToken = response.metadata?.continue;

      // 如果是第一页，记录 resourceVersion
      if (!resourceVersion && response.metadata?.resourceVersion) {
        resourceVersion = response.metadata.resourceVersion;
      }
    } while (continueToken); // 继续直到没有更多数据

    return {data: allRecords};
  } catch (error) {
    return {
      error: {
        status: 'CUSTOM_ERROR' as 'CUSTOM_ERROR',
        error: `获取元素失败: ${error}`,
      },
    };
  }
}
