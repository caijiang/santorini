import { AsyncThunkAction, Dispatch } from '@reduxjs/toolkit';
import { useDispatch } from 'react-redux';
import * as React from 'react';
import { useEffect, useState } from 'react';

interface X<T> {
  unwrap(): Promise<T>;
}

/**
 * 直接的 dispatch 非常不好用
 * @param action
 * @param dispatch
 */
export async function unwrapAsyncThunkAction<T>(
  action: AsyncThunkAction<T, any, any>,
  dispatch: Dispatch<any>
) {
  const x = dispatch(action) as unknown as X<T>;
  return x.unwrap();
}

/**
 *
 * @param action
 * @param deps action 不稳定，需要声明实际依赖
 */
export function useUnwrapAsyncThunkAction<T>(
  action: AsyncThunkAction<T, any, any>,
  deps: React.DependencyList
) {
  const dispatch = useDispatch();
  const [result, setResult] = useState<T>();

  useEffect(() => {
    unwrapAsyncThunkAction(action, dispatch).then(setResult);
  }, deps);
  return result;
}
