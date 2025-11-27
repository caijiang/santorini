import { useNamespacesQuery } from '../apis/common';
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
