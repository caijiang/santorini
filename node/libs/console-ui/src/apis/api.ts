import { apiBase } from '@private-everest/app-support';
import _ from 'lodash';

type X = Parameters<typeof apiBase>;

function withPrefix(args: any, prefix: string) {
  if (!args) return args;
  if (_.isString(args)) {
    return prefix + args;
  }
  if (_.isObjectLike(args)) {
    const { url, ...other } = args;
    if (url) {
      return {
        url: prefix + url,
        ...other,
      };
    } else {
      console.warn('请求 args 没有携带 url', args);
      return args;
    }
  }
  console.warn('未知的请求 args', typeof args);
  return args;
}

export const stBaseQuery = async (
  args: X[0],
  api: X[1],
  extraOptions: X[2]
) => {
  return apiBase(withPrefix(args, '/api'), api, extraOptions);
};
