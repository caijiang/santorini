import { useNamespacesQuery } from '../apis/kubernetes/common';
import { useEnvsQuery } from '../apis/env';
import { useCurrentLoginUserQuery } from '@private-everest/app-support';

export function useEnvs() {
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
