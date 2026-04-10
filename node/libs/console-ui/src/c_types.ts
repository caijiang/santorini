import { HpaLiveSliceState } from './slices/hpaLiveSlice';
import { createSelector } from '@reduxjs/toolkit';
import { HostsSliceState } from './slices/hostSlice';

export interface User {
  id: string;
}

export interface ModuleState {
  hpaLive: HpaLiveSliceState;
  hostsSlice: HostsSliceState;
}

export const createAppSelector = createSelector.withTypes<ModuleState>();
