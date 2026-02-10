import { Spin } from 'antd';
import * as React from 'react';
import { useGetDeploymentsQuery } from '../../apis/kubernetes/service';
import DeploymentStatus from '../kubernetes/tommy/DeploymentStatus';
import { ServiceDeployment } from './types';

interface ServiceDeploymentDeployedStatusProps extends ServiceDeployment {}

/**
 * 服务在已部署环境的状况
 * @constructor
 */
const ServiceDeploymentDeployedStatus: React.FC<
  ServiceDeploymentDeployedStatusProps
> = ({ service: { id }, envId }) => {
  const { data, isLoading, refetch } = useGetDeploymentsQuery(
    {
      namespace: envId,
      name: id,
      labelSelectors: ['santorini.io/service-type', `santorini.io/id=${id}`],
    },
    {
      skipPollingIfUnfocused: true,
      // 每隔 3 分钟重新拉取数据
      pollingInterval: 3 * 60 * 1000,
    }
  );
  // const { data, isLoading, refetch } = useDeploymentsQuery({
  //   namespace: envId,
  //   labelSelectors: ['santorini.io/service-type', `santorini.io/id=${id}`],
  // });

  if (isLoading) {
    return <Spin />;
  }
  if (!data) {
    return undefined;
  }
  return <DeploymentStatus refresh={refetch} data={data.status} />;
};

export default ServiceDeploymentDeployedStatus;
