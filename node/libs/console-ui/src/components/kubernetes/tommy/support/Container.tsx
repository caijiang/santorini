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

const PC = (props: ContainerProps) => {
  // console.log('ps:', props);
  return (
    <>
      <ProDescriptions
        title={`容器-${props.data.name}`}
        dataSource={props}
        columns={[
          {
            title: 'image',
            dataIndex: ['data', 'image'],
            copyable: true,
            // @ts-ignore
            filled: true,
          },
          {
            title: 'imageId',
            dataIndex: ['status', 'imageID'],
            copyable: true,
            // @ts-ignore
            filled: true,
          },
          {
            title: '重启次数',
            dataIndex: ['status', 'restartCount'],
            copyable: true,
          },
        ]}
      />
      {props.status?.lastState?.terminated && (
        <ProDescriptions
          title={`容器-${props.data.name}-退出信息`}
          dataSource={props.status?.lastState?.terminated}
          columns={[
            {
              title: '退出码',
              dataIndex: 'exitCode',
              copyable: true,
            },
            {
              title: 'startedAt',
              dataIndex: 'startedAt',
              valueType: 'dateTime',
            },
            {
              title: 'finishedAt',
              dataIndex: 'finishedAt',
              valueType: 'dateTime',
            },
            {
              title: 'message',
              dataIndex: 'message',
              copyable: true,
            },
            {
              title: 'reason',
              dataIndex: 'reason',
              copyable: true,
            },
            {
              title: 'signal',
              dataIndex: 'signal',
              copyable: true,
            },
          ]}
        />
      )}
    </>
  );
};

const Container: React.FC<ContainerProps> = ({ data, status }) => {
  if (!status) {
    return <PC data={data} status={status} />;
  }
  if (status.lastState?.terminated) {
    return (
      <Badge.Ribbon
        text={<ContainerStateTerminated data={status.lastState.terminated} />}
        color="red"
      >
        <PC data={data} status={status} />
      </Badge.Ribbon>
    );
  }
  if (status.lastState?.waiting) {
    return (
      <Badge.Ribbon
        text={<ContainerStateWaiting data={status.lastState.waiting} />}
        color="volcano"
      >
        <PC data={data} status={status} />
      </Badge.Ribbon>
    );
  }
  if (status.lastState?.running) {
    return (
      <Badge.Ribbon
        text={<ContainerStateRunning data={status.lastState.running} />}
        color="green"
      >
        <PC data={data} status={status} />
      </Badge.Ribbon>
    );
  }
  return <PC data={data} status={status} />;
};

export default Container;
