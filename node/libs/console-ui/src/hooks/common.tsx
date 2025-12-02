import { useNamespacesQuery } from '../apis/kubernetes/common';
import { useEnvsQuery } from '../apis/env';

export function useEnvs() {
  const { data } = useNamespacesQuery(undefined, {
    refetchOnFocus: true,
  });
  const ids = data?.items
    ?.filter((it) => !!it?.metadata?.name)
    ?.map((it) => it.metadata!!);
  // ?.map(it=>it)
  const { data: ee } = useEnvsQuery(ids, {
    skip: !ids || ids.length == 0,
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
