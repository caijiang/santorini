import { useNamespacesQuery } from '../apis/kubernetes/common';
import { CUEnv, useEnvsQuery } from '../apis/env';
import { useCurrentLoginUserQuery } from '@private-everest/app-support';

export function useEnvs(): CUEnv[] | undefined {
  const { data: currentUser } = useCurrentLoginUserQuery(undefined);
  const loginAsManager =
    currentUser?.grantAuthorities?.includes('ROLE_MANAGER');
  // 非管理员 需要避开直接调用  useNamespacesQuery
  const { data } = useNamespacesQuery(
    {},
    {
      refetchOnFocus: true,
      skip: !loginAsManager,
    }
  );
  const ids = data
    ?.filter((it) => !!it?.metadata?.name)
    ?.map((it) => it.metadata!!);
  // ?.map(it=>it)
  // 2 种情况跳过 获取
  const skipQueryEnvs =
    loginAsManager === undefined ||
    (loginAsManager && (!ids || ids.length == 0));
  const { data: ee } = useEnvsQuery(ids, {
    skip: skipQueryEnvs,
  });
  // 如果存在 data 而且存在 ee, 同时 ee 并不包含 data 则添加一条
  if (ids && ee) {
    const notInEE = ids
      .filter((it) => ee.every((e1) => e1.id != it.name))
      .map((it) => ({
        id: it.name!!,
        name: it.name!!,
        production: false,
      }));
    return [...ee, ...notInEE];
  }
  return ee;
}

/**
 * @param envId namespace
 * @return CUEnv undefined 是还没查询完成,null 是查询完成了 但没找到
 */
export function useEnv(envId: string | undefined) {
  const envs = useEnvs();
  if (!envs) return undefined;
  const find = envs.find((it) => it.id == envId);
  if (find) return find;
  return null;
}
