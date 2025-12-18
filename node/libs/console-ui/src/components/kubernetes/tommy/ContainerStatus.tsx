import * as React from 'react';
import { ProCard } from '@ant-design/pro-components';
import { IIoK8sApiCoreV1ContainerStatus } from 'kubernetes-models/v1/ContainerStatus';
import { Statistic } from 'antd';

interface ContainerStatusProps {
  data: IIoK8sApiCoreV1ContainerStatus;
}

const ContainerStatus: React.FC<ContainerStatusProps> = ({ data }) => {
  return (
    <ProCard title={data.name}>
      {data.lastState?.terminated && (
        <Statistic
          title="最后崩溃"
          value={data.lastState?.terminated?.reason}
        />
      )}
    </ProCard>
  );
};

export default ContainerStatus;
