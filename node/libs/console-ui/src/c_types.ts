import { HpaLiveSliceState } from './slices/hpaLiveSlice';
import { createSelector } from '@reduxjs/toolkit';

export interface User {
  id: string;
}

export interface ModuleState {
  hpaLive: HpaLiveSliceState;
}

export const createAppSelector = createSelector.withTypes<ModuleState>();
