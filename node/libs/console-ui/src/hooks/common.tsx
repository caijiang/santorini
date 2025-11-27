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

export function useEnv(envId: string | undefined) {
  const envs = useEnvs();
  return envs?.find((it) => it.id == envId);
}
