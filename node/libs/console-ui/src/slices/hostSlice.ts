import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { HostSummary, useHostsV2Query } from '../apis/host';
import { useMemo } from 'react';
import { useSelector } from 'react-redux';
import { ModuleState } from '../c_types';

type LHS = HostSummary & { namespace: string };

export interface HostsSliceState {
  data: LHS[];
}

const defaultState: HostsSliceState = { data: [] };

export const hostsSlice = createSlice({
  name: 'hostsSlice',
  initialState: defaultState,
  reducers: {
    newHost: (store, action: PayloadAction<LHS>) => {
      store.data = [...store.data, action.payload];
    },
  },
});

export const { newHost } = hostsSlice.actions;

function mergeH(data: HostSummary[] | undefined, localHosts: LHS[]) {
  if (!data) {
    if (localHosts.length === 0) return undefined;
    return localHosts;
  }
  // 如果 localHosts 的已经存在于 data, 那么就忽略它
  const newInLocalHosts = localHosts.filter((it) =>
    // 所有都不符合data里的元素
    data.every((x) => x.hostname !== it.hostname)
  );

  return [...data, ...newInLocalHosts];
}

export function useHosts(namespace: string) {
  const apiResult = useHostsV2Query(namespace);
  const localHosts = useSelector((state: ModuleState) =>
    state.hostsSlice.data.filter((it) => it.namespace === namespace)
  );
  const data = useMemo(
    () => mergeH(apiResult.data, localHosts),
    [apiResult.data, localHosts]
  );

  return {
    ...apiResult,
    data,
  };
}
