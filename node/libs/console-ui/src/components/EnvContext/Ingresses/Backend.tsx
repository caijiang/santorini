import { IngressPath } from './df';
import * as React from 'react';
import { Badge, Space, Spin } from 'antd';
import { ArrowRightOutlined } from '@ant-design/icons';
import { useServiceByIdQuery } from '../../../apis/service';

interface BackendProps {
  data: IngressPath;
}

const Backend: React.FC<BackendProps> = ({
  data: {
    path: {
      backend: { service },
    },
  },
}) => {
  const name = service?.name;
  const port = service?.port;
  const { data, isLoading } = useServiceByIdQuery(name ?? 'NEVER', {
    skip: !name,
  });
  // 寻找这个服务是否是我们的服务，如果是的话 采用
  if (!name) {
    return (
      <Space>
        <ArrowRightOutlined /> ??
      </Space>
    );
  }
  // 载入中
  if (isLoading) {
    return (
      <Space>
        <ArrowRightOutlined />
        <Spin />
      </Space>
    );
  }
  // 非我方托管服务
  if (!data) {
    return (
      <Badge.Ribbon color={'volcano'} text={'非托管服务'}>
        <Space>
          <ArrowRightOutlined />
          {name}:{port?.number}
        </Space>
      </Badge.Ribbon>
    );
  }
  return (
    <Space>
      <ArrowRightOutlined />
      {data.name}:
      {
        data.ports.find(
          (it) => it.number == port?.number || it.name == port?.name
        )?.name
      }
    </Space>
  );
};

export default Backend;
