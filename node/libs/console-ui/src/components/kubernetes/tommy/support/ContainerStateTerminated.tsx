import { IIoK8sApiCoreV1ContainerStateTerminated } from 'kubernetes-models/v1/ContainerStateTerminated';
import { Space } from 'antd';

export default ({
  data,
}: {
  data: IIoK8sApiCoreV1ContainerStateTerminated;
}) => {
  return (
    <Space>
      {data.startedAt ? `之前于 ${data.startedAt}终止` : '之前终止'}
      {`${data.exitCode}`}
      {data.reason && `(${data.reason})`}
    </Space>
  );
};
