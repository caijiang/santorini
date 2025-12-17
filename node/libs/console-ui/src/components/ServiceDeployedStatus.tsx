import { ServiceConfigData } from '../apis/service';
import { Empty, Space, Spin } from 'antd';
import * as React from 'react';
import { useEnvs } from '../hooks/common';
import { kubeServiceApi } from '../apis/kubernetes/service';
import Env from './env/Env';
import DeploymentStatus from './kubernetes/tommy/DeploymentStatus';
import { CUEnv } from '../apis/env';
import { IDeployment } from 'kubernetes-models/apps/v1/Deployment';
import { queryDeploymentByEnvs } from '../slices/queryDeploymentByEnvs';
import { useUnwrapAsyncThunkAction } from '../slices/support/common';
import _ from 'lodash';
import { useDispatch } from 'react-redux';

interface ServiceDeployedStatusProps {
  service: ServiceConfigData;
}

export function queryDeploymentEnvPair(
  data: IDeployment[] | undefined,
  envs: CUEnv[] | undefined
) {
  return (
    data &&
    envs &&
    data
      .filter((d) => envs.some((it) => it.id == d.metadata?.namespace))
      .map((d) => ({
        deployment: d,
        env: envs.find((it) => it.id == d.metadata?.namespace)!!,
      }))
  );
}

export function queryDeploymentEnvPair2(
  record: Record<string, IDeployment | undefined> | undefined,
  envs: CUEnv[] | undefined
) {
  if (!record) return undefined;
  return _.keys(record).map((id) => ({
    deployment: record?.[id],
    env: envs?.find((it) => it.id == id)!!,
  }));
}

/**
 * 服务在已部署环境的状况
 * @constructor
 */
const ServiceDeployedStatus: React.FC<ServiceDeployedStatusProps> = ({
  service: { id },
}) => {
  const envs = useEnvs();
  const dispatch = useDispatch();
  const wellResult = useUnwrapAsyncThunkAction(
    queryDeploymentByEnvs({
      array: envs,
      toArg: (it) => ({
        name: id,
        namespace: it.id,
      }),
    }),
    [envs, id]
  );
  const x = queryDeploymentEnvPair2(wellResult, envs);

  if (!x) {
    return <Spin />;
  }

  if (x.length == 0) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        styles={{
          image: {
            height: 15,
          },
        }}
        style={{
          padding: 0,
          margin: 0,
        }}
      />
    );
  }
  return (
    <Space direction={'vertical'}>
      {x.map((xx) => (
        <Space key={xx.env.id}>
          <Env data={xx.env} brief />
          <DeploymentStatus
            refresh={() => {
              return dispatch(
                kubeServiceApi.util.invalidateTags(['deployments'])
              );
            }}
            data={xx?.deployment?.status}
          />
        </Space>
      ))}
    </Space>
  );
};

export default ServiceDeployedStatus;
