import {
  createAsyncThunk,
  createSlice,
  ListenerEffectAPI,
  PayloadAction,
} from '@reduxjs/toolkit';
import { CUEnv } from '../apis/env';
import {
  DeploymentDeployData,
  serviceApi,
  ServiceConfigData,
} from '../apis/service';
import _ from 'lodash';
import { commonApi } from '../apis/kubernetes/common';
import { App } from 'cdk8s';
import { ServiceChart } from './ServiceChart';
import { listenerMiddleware } from '../module-private';
import { ServiceDeployToKubernetesProps } from './deployService';

interface DeployHelpState {
  image?: string;
  targetEnv?: CUEnv;
  secretNames?: string[];
  service?: ServiceConfigData;
}

function toLastReleaseDeploymentSummary(
  state: DeployHelpState
): DeploymentDeployData | undefined {
  if (state.image && state.secretNames) {
    const st = state.image.split(':', 2);
    if (st.length == 1) {
      return {
        imageRepository: state.image,
        pullSecretName: state.secretNames,
      };
    }
    return {
      imageRepository: st[0],
      imageTag: st[1],
      pullSecretName: state.secretNames,
    };
  }
  return undefined;
}

/**
 * 在部署过程中需要和用户交互
 */
export const deployServiceSlice = createSlice({
  name: 'service/deployHelp',
  initialState: {} as DeployHelpState,
  reducers: {
    updateSecretNames: (state, action: PayloadAction<string[] | undefined>) => {
      state.secretNames = action.payload;
    },
    updateImage: (state, action: PayloadAction<string | undefined>) => {
      state.image = action.payload;
    },
    reset: (
      state,
      action: PayloadAction<{ env: CUEnv; service: ServiceConfigData }>
    ) => {
      state.image = undefined;
      const { env, service } = action.payload;
      if (state.targetEnv?.id != env?.id) {
        state.targetEnv = env;
        state.secretNames = undefined;
      }
      state.service = service;
    },
  },
  extraReducers: (builder) => {
    builder.addMatcher(
      commonApi.endpoints.secretByNamespace.matchFulfilled,
      (state, action) => {
        state.secretNames = action.payload
          .filter((it) => it.metadata?.name)
          .map((it) => it.metadata!!.name!!);
      }
    );
  },
});

const changeCurrentEnv = createAsyncThunk(
  'service/deployHelp/changeCurrentEnv',
  async (
    input: { env: CUEnv; service: ServiceConfigData },
    { getState, dispatch }
  ) => {
    // @ts-ignore
    const state = getState()[deployServiceSlice.name] as DeployHelpState;
    const { env } = input;
    const f = state.targetEnv?.id != env?.id;
    dispatch(deployServiceSlice.actions.reset(input));
    if (f) {
      await dispatch(commonApi.endpoints.secretByNamespace.initiate(env.id));
    }
  }
);



const toStartP2 = async (
  _: any,
  { getState, dispatch }: ListenerEffectAPI<any, any>
) => {
  // @ts-ignore
  const root: DeployHelpState = getState()[deployServiceSlice.name];
  const r = toLastReleaseDeploymentSummary(root);
  if (r) {
    const { service, targetEnv } = root;
    if (service && targetEnv) {
      await dispatch(
        deployServiceToEnvP2({ service, env: targetEnv, envRelated: r })
      );
    }
  }
};
listenerMiddleware.startListening({
  actionCreator: deployServiceSlice.actions.updateImage,
  effect: toStartP2,
});
listenerMiddleware.startListening({
  actionCreator: deployServiceSlice.actions.updateSecretNames,
  effect: toStartP2,
});
/**
 * 部署到环境的第二阶段
 */
const deployServiceToEnvP2 = createAsyncThunk(
  'service/deploy/p2',
  async (input: ServiceDeployToKubernetesProps) => {
    // 要注意 id 部署物或者其他什么 都有个名字 比如 a-deployment 但我们的 id 是其中的 a
    const app = new App({});
    new ServiceChart(
      app,
      input.service.id,
      {
        disableResourceNameHashes: true,
      },
      input
    );
    console.log(app.synthYaml());
  }
);
/**
 * 首先获取服务配置，部署的过程中，检查一些列情况
 */
export const startDeployServiceToEnv = createAsyncThunk(
  'service/deploy',
  async (
    input: { service: ServiceConfigData | string; env: CUEnv },
    { dispatch, abort }
  ) => {
    let serviceData: ServiceConfigData;
    if (_.isString(input.service)) {
      const s = await dispatch(
        serviceApi.endpoints.serviceById.initiate(input.service)
      ).unwrap();
      console.log('serviceById result:', s);
      if (!s) {
        abort('找不到该名字的服务配置');
        return;
      }
      serviceData = s;
    } else {
      serviceData = input.service;
    }
    // 现在 环境那块先跳过
    // 最近发布记录
    // 找到就填入，找不到就要求填入
    const lastRelease = await dispatch(
      serviceApi.endpoints.lastRelease.initiate({
        serviceId: serviceData.id,
        envId: input.env.id,
      })
    ).unwrap();

    console.log('lastRelease:', lastRelease);
    if (lastRelease) {
      await dispatch(
        deployServiceToEnvP2({
          service: serviceData,
          env: input.env,
          envRelated: lastRelease,
        })
      );
    } else {
      // 最终它还是会 deployServiceToEnvP2
      await dispatch(
        changeCurrentEnv({
          env: input.env,
          service: serviceData,
        })
      );
    }
  }
);
