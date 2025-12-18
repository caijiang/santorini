import { ServiceConfigData } from '../apis/service';
import { Empty, Spin } from 'antd';
import * as React from 'react';
import { useDeploymentQuery } from '../apis/kubernetes/service';
import DeploymentStatus from './kubernetes/tommy/DeploymentStatus';

interface ServiceDeployedStatusForEnvProps {
  service: ServiceConfigData;
  envId: string;
}

/**
 * 服务在已部署环境的状况
 * @constructor
 */
const ServiceDeployedStatusForEnv: React.FC<
  ServiceDeployedStatusForEnvProps
> = ({ service: { id }, envId }) => {
  const { data, isLoading, refetch } = useDeploymentQuery({
    namespace: envId,
    name: id,
    labelSelectors: ['santorini.io/service-type', `santorini.io/id=${id}`],
  });
  // const { data, isLoading, refetch } = useDeploymentsQuery({
  //   namespace: envId,
  //   labelSelectors: ['santorini.io/service-type', `santorini.io/id=${id}`],
  // });

  if (isLoading) {
    return <Spin />;
  }
  if (!data) {
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
  return <DeploymentStatus refresh={refetch} data={data.status} />;
};

export default ServiceDeployedStatusForEnv;
