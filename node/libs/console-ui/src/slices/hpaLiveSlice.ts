import { listenerMiddleware } from '../module-private';
import { createSlice, PayloadAction, WritableDraft } from '@reduxjs/toolkit';
import { EventSourcePolyfill } from 'event-source-polyfill';
import _ from 'lodash';
import { createAppSelector } from '../c_types';

// createSelectorHook()
// createDraftSelector()
// createDraftSafeSelector()

// service-env
export interface OneHpaData {
  type: 'Utilization' | 'Value' | 'AverageValue';
  name: 'cpu' | 'memory';
  target: number;
  timelines: {
    time: number;
    current: number;
  }[];
}

export function humanReadMessage({ name, type }: OneHpaData, data: number) {
  if (name === 'cpu') {
    if (type === 'Utilization') return `平均CPU使用率${data}%`;
    if (type === 'AverageValue') return `平均CPU占用${data}核`;
    if (type === 'Value') return `总CPU占用${data}核`;
  }
  if (name === 'memory') {
    if (type === 'Utilization') return `平均内存使用率${data}%`;
    if (type === 'AverageValue') return `平均内存占用${data / (1024 * 1024)}M`;
    if (type === 'Value') return `总内存占用${data / (1024 * 1024)}核`;
  }
  throw new Error(`并不认识:${name},${type}`);
}

export interface HpaLiveSliceState {
  map: Record<string, OneHpaData>;
}

const defaultState: HpaLiveSliceState = { map: {} };

interface HpaStatusData {
  /**
   * 秒数
   */
  instant: string;
  serviceId: string;
  envId: string;
  name: 'cpu' | 'memory';
  type: 'Utilization' | 'Value' | 'AverageValue';
  value: string;
  target: string;
}

function mergeInto(
  current: WritableDraft<OneHpaData>,
  newList: HpaStatusData[],
  lastOne: HpaStatusData
) {
  if (
    current.target !== parseFloat(lastOne.target) ||
    current.name !== lastOne.name ||
    current.type !== lastOne.type
  ) {
    const n = newOneHpaData(lastOne, newList);
    current.type = n.type;
    current.name = n.name;
    current.target = n.target;
    current.timelines = n.timelines;
  } else {
    // 合并并且筛选
    const newLines = [
      ...current.timelines,
      ...newList.map(({ instant, value }) => ({
        time: parseInt(instant),
        current: parseFloat(value),
      })),
    ];
    current.timelines = _.uniqBy(newLines, 'time');
  }
}

function newOneHpaData(lastOne: HpaStatusData, newList: HpaStatusData[]) {
  return {
    type: lastOne.type,
    name: lastOne.name,
    target: parseFloat(lastOne.target),
    timelines: newList.map(({ instant, value }) => ({
      time: parseInt(instant),
      current: parseFloat(value),
    })),
  };
}

export const hpaLiveSlice = createSlice({
  name: 'hpaLive',
  initialState: defaultState,
  reducers: {
    start: () => {
      return defaultState;
    },
    newData: (store, action: PayloadAction<string>) => {
      const input = JSON.parse(action.payload) as HpaStatusData[];
      const g = _.groupBy(input, (it) => `${it.serviceId}-${it.envId}`);
      _.keys(g).forEach((oneId) => {
        // 获取其最大秒的 target
        const list = g[oneId];
        const lastOne = _.maxBy(list, (it) => parseInt(it.instant));
        if (lastOne) {
          const newList = list.filter(
            (it) =>
              it.type === lastOne.type &&
              it.target === lastOne.target &&
              it.name === lastOne.name
          );
          const current = store.map[oneId];
          if (current) {
            mergeInto(current, newList, lastOne);
          } else {
            store.map[oneId] = newOneHpaData(lastOne, newList);
          }
        }
      });
    },
  },
});

let globalSource: EventSourcePolyfill | undefined = undefined;

listenerMiddleware.startListening({
  actionCreator: hpaLiveSlice.actions.start,
  effect: async (_, api) => {
    if (!globalSource) {
      globalSource = new EventSourcePolyfill('/api/hpaStatus', {
        withCredentials: true,
        headers: {
          'x-everest': '1',
        },
      });
      globalSource.onmessage = (e) => {
        // 只维护 1 小时内的数据
        // 按 s-e dict好，跟新标准不同的数据直接丢弃
        api.dispatch(hpaLiveSlice.actions.newData(e.data));
      };
    }
  },
});

/**
 * 定制一个可以获取 hpa 状态数据的 selector
 */
export const selectHpaLiveByService = createAppSelector(
  [
    ({ hpaLive }) => {
      // console.log('input testArg:', testArg);
      return hpaLive.map;
    },
    (_, serviceId: string, envId: string) => ({ serviceId, envId }),
  ],
  (it, test) => {
    const oneId = `${test.serviceId}-${test.envId}`;
    return it[oneId];
  }
);
