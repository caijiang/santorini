import { ServiceConfigData } from '../apis/service';
import { Empty, Space, Spin } from 'antd';
import * as React from 'react';
import { useEnvs } from '../hooks/common';
import { useDeploymentsQuery } from '../apis/kubernetes/service';
import Env from './env/Env';
import DeploymentStatus from './kubernetes/tommy/DeploymentStatus';

interface ServiceDeployedStatusProps {
  service: ServiceConfigData;
}

/**
 * 服务在已部署环境的状况
 * @constructor
 */
const ServiceDeployedStatus: React.FC<ServiceDeployedStatusProps> = ({
  service: { id },
}) => {
  const envs = useEnvs();
  const { data, isLoading } = useDeploymentsQuery({
    labelSelectors: ['santorini.io/service-type', `santorini.io/id=${id}`],
  });
  const x =
    data &&
    envs &&
    data
      .filter((d) => envs.some((it) => it.id == d.metadata?.namespace))
      .map((d) => ({
        deployment: d,
        env: envs.find((it) => it.id == d.metadata?.namespace)!!,
      }));

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
          <DeploymentStatus data={xx.deployment.status} />
        </Space>
      ))}
    </Space>
  );
};

export default ServiceDeployedStatus;
