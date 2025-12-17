import { createAsyncThunk } from '@reduxjs/toolkit';
import { ApiEndpointQuery, QueryDefinition } from '@reduxjs/toolkit/query';

export interface LoopRTKQueryArgs<LoopElement, QueryArg> {
  /**
   * 迭代的入参，如果未初始化，我们也就等待。
   */
  array: LoopElement[] | undefined;
  toArg: (element: LoopElement) => QueryArg;
}

export function createLoopRTKQueryThunk<
  LoopElement,
  KeyOfLoopElement extends string | number | symbol,
  QueryArg,
  QueryResult
>(
  type: string,
  toKey: (element: LoopElement) => KeyOfLoopElement,
  query: ApiEndpointQuery<QueryDefinition<QueryArg, any, any, QueryResult>, any>
) {
  return createAsyncThunk<
    Record<KeyOfLoopElement, QueryResult | undefined> | undefined,
    LoopRTKQueryArgs<LoopElement, QueryArg>
  >(type, async ({ array, toArg }, { dispatch }) => {
    if (!array) {
      return undefined;
    }
    const list = await Promise.all(
      array.map((element) => {
        const key = toKey(element);
        const arg = toArg(element);
        return dispatch(query.initiate(arg))
          .unwrap()
          .then((result) => ({
            key,
            result,
          }));
      })
    );
    return list.reduce<Record<KeyOfLoopElement, QueryResult>>((acc, cur) => {
      acc[cur.key] = cur.result;
      return acc;
    }, {} as Record<KeyOfLoopElement, QueryResult>);
  });
}
