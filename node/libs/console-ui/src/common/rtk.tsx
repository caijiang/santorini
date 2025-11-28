import { Dispatch } from '@reduxjs/toolkit';

interface ErrorAble {
  error?: {
    message?: any;
  };
}

export async function dispatchActionThrowIfError(
  dispatch: Dispatch<any>,
  action: any
) {
  const result1: ErrorAble = await dispatch(action);
  const { error, ...other } = result1;
  if (error) {
    console.warn('error:', error);
    if (error.message) throw error.message;
    console.debug('dispatch result without error:', other);
    throw '未知错误发生了';
  }
  console.info('dispatch result without error:', other);
}
