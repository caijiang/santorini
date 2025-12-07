import { ServiceConfigData } from '../apis/service';
import { Empty, Space, Spin } from 'antd';
import * as React from 'react';
import { useEnvs } from '../hooks/common';
import { useDeploymentsQuery } from '../apis/kubernetes/service';
import Env from './env/Env';
import DeploymentStatus from './kubernetes/tommy/DeploymentStatus';
import { CUEnv } from '../apis/env';
import { IDeployment } from 'kubernetes-models/apps/v1/Deployment';

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

/**
 * 服务在已部署环境的状况
 * @constructor
 */
const ServiceDeployedStatus: React.FC<ServiceDeployedStatusProps> = ({
  service: { id },
}) => {
  const envs = useEnvs();
  const { data, isLoading, refetch } = useDeploymentsQuery({
    labelSelectors: ['santorini.io/service-type', `santorini.io/id=${id}`],
  });
  const x = queryDeploymentEnvPair(data, envs);

  if (!x || isLoading) {
    return <Spin />;
  }
  // console.log('x:', x);

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
          <DeploymentStatus refresh={refetch} data={xx.deployment.status} />
        </Space>
      ))}
    </Space>
  );
};

export default ServiceDeployedStatus;
