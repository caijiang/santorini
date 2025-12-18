import { Space } from 'antd';
import { IIoK8sApiCoreV1ContainerStateWaiting } from 'kubernetes-models/v1';

export default ({ data }: { data: IIoK8sApiCoreV1ContainerStateWaiting }) => {
  return (
    <Space>
      {data.message && data.message}
      {data.reason && `(${data.reason})`}
    </Space>
  );
};
