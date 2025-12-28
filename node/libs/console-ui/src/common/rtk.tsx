import {
  AsyncThunkAction,
  Dispatch,
  PayloadAction,
  ThunkAction,
  UnknownAction,
} from '@reduxjs/toolkit';
import {
  MutationActionCreatorResult,
  MutationDefinition,
} from '@reduxjs/toolkit/query';

type Error = {
  message?: any;
};

// interface ErrorAble {
//   error?: Error;
// }

export async function dispatchThunkActionThrowIfError<T>(
  dispatch: Dispatch<any>,
  action: ThunkAction<
    MutationActionCreatorResult<MutationDefinition<any, any, any, T>>,
    any,
    any,
    UnknownAction
  >
) {
  const result1 = dispatch(action) as unknown as {
    unwrap: () => Promise<T>;
  };
  const result2 = await result1.unwrap();
  console.debug('dispatchThunkActionThrowIfError result:', result2);

  return result2;
}

export async function dispatchAsyncThunkActionThrowIfError<T>(
  dispatch: Dispatch<any>,
  action: AsyncThunkAction<T, any, any>
) {
  // 其实 meta 是: arg,requestId,requestStatus
  const result1: PayloadAction<T, string, never, Error> = await dispatch(
    action as any
  );
  const { error, ...other } = result1;
  if (error) {
    console.warn('error:', error);
    if (error.message) throw error.message;
    console.debug('dispatch result without error:', other);
    throw '未知错误发生了';
  }

  console.debug('dispatch result success:', other);
  return other.payload;
}
