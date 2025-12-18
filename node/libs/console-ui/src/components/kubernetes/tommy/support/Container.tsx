import { IContainer, IContainerStatus } from 'kubernetes-models/v1';
import * as React from 'react';
import { ProDescriptions } from '@ant-design/pro-components';
import { Badge } from 'antd';
import ContainerStateTerminated from './ContainerStateTerminated';
import ContainerStateWaiting from './ContainerStateWaiting';
import ContainerStateRunning from './ContainerStateRunning';

interface ContainerProps {
  data: IContainer;
  status: IContainerStatus | undefined;
}

const PC = ({ data }: { data: IContainer }) => {
  return (
    <ProDescriptions
      title={`容器-${data.name}`}
      dataSource={data}
      columns={[
        {
          title: 'image',
          dataIndex: 'image',
          copyable: true,
        },
      ]}
    />
  );
};

const Container: React.FC<ContainerProps> = ({ data, status }) => {
  if (!status) {
    return <PC data={data} />;
  }
  if (status.lastState?.terminated) {
    return (
      <Badge.Ribbon
        text={<ContainerStateTerminated data={status.lastState.terminated} />}
        color="red"
      >
        <PC data={data} />
      </Badge.Ribbon>
    );
  }
  if (status.lastState?.waiting) {
    return (
      <Badge.Ribbon
        text={<ContainerStateWaiting data={status.lastState.waiting} />}
        color="volcano"
      >
        <PC data={data} />
      </Badge.Ribbon>
    );
  }
  if (status.lastState?.running) {
    return (
      <Badge.Ribbon
        text={<ContainerStateRunning data={status.lastState.running} />}
        color="green"
      >
        <PC data={data} />
      </Badge.Ribbon>
    );
  }
  return <PC data={data} />;
};

export default Container;
