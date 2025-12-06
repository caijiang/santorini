import { fetchBaseQuery, retry } from '@reduxjs/toolkit/query/react';
import { tokenApi } from '../token';

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
      throw new Error('please retry.');
      // retry
      // return {
      //   data: undefined as unknown,
      //   error: {} as unknown as undefined,
      //   isUninitialized: false,
      //   isLoading: false,
      //   isFetching: false,
      //   isSuccess: false,
      //   isError: true,
      //   refetch: () => {},
      // };
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
