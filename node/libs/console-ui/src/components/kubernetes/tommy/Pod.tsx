import { IPod } from 'kubernetes-models/v1';
import * as React from 'react';
import Metadata from './support/Metadata';
import { ProDescriptions } from '@ant-design/pro-components';
import { stringRecordRender } from './support/renders';
import Container from './support/Container';

interface PodProps {
  data: IPod;
}

/**
 * 完全显示一个pod
 * @constructor
 */
const Pod: React.FC<PodProps> = ({ data }) => {
  const cs = data.spec?.containers?.map((c) => ({
    data: c,
    status: data.status?.containerStatuses?.find((cs) => cs.name == c.name),
  }));
  return (
    <>
      {data.metadata && <Metadata data={data.metadata} />}
      {data.spec && (
        <ProDescriptions
          title={'规格'}
          dataSource={data.spec}
          columns={[
            {
              title: 'nodeSelector',
              dataIndex: 'nodeSelector',
              render: (_, entity) => stringRecordRender(entity.nodeSelector),
              // @ts-ignore
              filled: true,
            },
            {
              title: 'nodeName',
              dataIndex: 'nodeName',
              copyable: true,
            },
            {
              title: 'terminationGracePeriodSeconds',
              dataIndex: 'terminationGracePeriodSeconds',
              valueType: 'digit',
            },
          ]}
        />
      )}
      {data.status && (
        <ProDescriptions
          title={'状态'}
          dataSource={data.status}
          columns={[
            {
              title: 'hostIP',
              dataIndex: 'hostIP',
              copyable: true,
            },
            {
              title: 'podIP',
              dataIndex: 'podIP',
              copyable: true,
            },
            {
              title: 'phase',
              dataIndex: 'phase',
              copyable: true,
            },
            {
              title: 'message',
              dataIndex: 'message',
              copyable: true,
            },
            {
              title: 'startTime',
              dataIndex: 'startTime',
              valueType: 'dateTime',
            },
          ]}
        />
      )}
      {cs?.map((c) => (
        <Container key={c.data.name} {...c} />
      ))}
    </>
  );
};

export default Pod;
