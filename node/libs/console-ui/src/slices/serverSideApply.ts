import { createAsyncThunk, createSlice, PayloadAction } from '@reduxjs/toolkit';
import {
  ApiEndpointMutation,
  FetchBaseQueryError,
  MutationDefinition,
} from '@reduxjs/toolkit/query';
import { ObjectContainer, PatchObjectContainer } from '../apis/kubernetes/type';
import _ from 'lodash';
import { IStatus } from '@kubernetes-models/apimachinery/apis/meta/v1';
import { IStatusCause } from '@kubernetes-models/apimachinery/apis/meta/v1/StatusCause';
import YAML from 'yaml';
import {
  readKubernetesField,
  removeKubernetesField,
  updateKubernetesField,
} from './kubernetesJsonPath';

interface ServerSideApplyInput<T> {
  api: ApiEndpointMutation<
    MutationDefinition<PatchObjectContainer, any, string, T>,
    any
  >;
  args: ObjectContainer;
}

/**
 * 应当自动合并的冲突字段管理员
 */
export const autoMergeFieldManagers = ['Mozilla'];

export type ConflictFieldSolution = 'force' | 'abandon' | 'merge';

export interface ServerSideApplyState {
  /**
   * 用户原始提交
   */
  initArgs?: ObjectContainer & ServerSideApplyArg;
  /**
   * 冲突的字段
   */
  originFields?: Record<string, string[]>;
  /**
   * 每个字段的解决方案
   */
  solutions?: Record<string, ConflictFieldSolution>;
  /**
   * 是否已放弃
   */
  userAbandon: boolean;
}

const initState: ServerSideApplyState = {
  userAbandon: false,
};

function extractManager(message?: string): string | undefined {
  if (!message) return undefined;
  // 匹配 conflict with "Mozilla"
  const match = message.match(/conflict with "([^"]+)"/);
  return match?.[1];
}

function groupConflictsByField(
  causes: IStatusCause[]
): Record<string, string[]> {
  return _.mapValues(
    _.groupBy(
      causes.filter((c) => c.field),
      (c) => c.field as string
    ),
    (group) =>
      _.uniq(
        group
          .map((c) => extractManager(c?.message))
          .filter((v) => !!v)
          .map((it) => it as string)
      )
  );
}

export const serverSideApplySlice = createSlice({
  name: 'serverSideApply',
  initialState: initState,
  reducers: {
    conflict: (state, action: PayloadAction<IStatus>) => {
      try {
        state.originFields = groupConflictsByField(
          action.payload?.details?.causes ?? []
        );
      } catch (e) {
        console.error('?:', e);
      }
    },
    abandon(state) {
      state.userAbandon = true;
    },
    init(_, action: PayloadAction<ObjectContainer & ServerSideApplyArg>) {
      return {
        ...initState,
        initArgs: action.payload,
        originFields: undefined,
        userAbandon: false,
        solutions: undefined,
      };
    },
    submit(
      state,
      action: PayloadAction<Record<string, ConflictFieldSolution>>
    ) {
      state.solutions = action.payload;
    },
  },
});

export interface ServerSideApplyArg {
  /**
   * 当前的数据
   */
  current: any;
}

async function patchWithSolution<T>(
  input: ObjectContainer & ServerSideApplyArg,
  solutions: Record<string, ConflictFieldSolution>,
  api: ApiEndpointMutation<
    MutationDefinition<PatchObjectContainer, any, string, T>,
    any
  >,
  dispatch: any
): Promise<T> {
  // 计划修改的 yaml
  const draft = YAML.parse(input.yaml!!);
  const anyForce = _.values(solutions).some((it) => it == 'force');
  _.keys(solutions)
    .filter((it) => solutions[it] != 'force')
    .forEach((it) => {
      if (solutions[it] == 'merge') {
        const v = readKubernetesField(input.current, it);
        updateKubernetesField(draft, it, v);
      } else if (solutions[it] == 'abandon') {
        removeKubernetesField(draft, it);
      }
    });

  return await dispatch(
    api.initiate({
      ...input,
      force: anyForce,
      yaml: YAML.stringify(draft),
    })
  ).unwrap();
}

/**
 * 致力于实现 https://kubernetes.io/zh-cn/docs/reference/using-api/server-side-apply/ 的功能切片
 */
export function createServerSideApplySliceHelper<T>(
  typePrefix: string,
  api: ServerSideApplyInput<T>['api']
) {
  return createAsyncThunk(
    typePrefix,
    async (
      input: ObjectContainer & ServerSideApplyArg,
      { dispatch, getState }
    ) => {
      try {
        dispatch(serverSideApplySlice.actions.init(input));
        return await dispatch(api.initiate(input)).unwrap();
      } catch (e) {
        const exception = e as FetchBaseQueryError;
        if (exception.status == 409 && _.isObject(exception.data)) {
          // 确认冲突了
          const status = exception.data as IStatus;
          // 自动合并么？
          const fields = groupConflictsByField(status.details?.causes ?? []);
          if (
            _.keys(fields).every((fieldName) =>
              fields[fieldName].some((co) =>
                autoMergeFieldManagers.some((it) => it == co)
              )
            )
          ) {
            console.log('所有字段都符合自动覆盖的特征:', fields);
            return await patchWithSolution(
              input,
              _.fromPairs(_.keys(fields).map((it) => [it, 'force'])),
              api,
              dispatch
            );
          } else {
            dispatch(serverSideApplySlice.actions.conflict(status));
            // 开始等待
            while (true) {
              const state = (
                getState() as { serverSideApply: ServerSideApplyState }
              ).serverSideApply;
              if (state.userAbandon) {
                break;
              }
              if (state.solutions) {
                break;
              }
              console.debug('wait for user inputs.');
              await new Promise((r) => setTimeout(r, 1000));
            }
            const state = (
              getState() as { serverSideApply: ServerSideApplyState }
            ).serverSideApply;
            if (state.userAbandon) {
              console.warn('提交有冲突，但用户放弃合并了');
              throw e;
            }
            return await patchWithSolution(
              input,
              state.solutions ?? {},
              api,
              dispatch
            );
          }
        }

        throw e;
      }
    }
  );
}
