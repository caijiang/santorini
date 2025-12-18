import { Space } from 'antd';
import { IIoK8sApiCoreV1ContainerStateRunning } from 'kubernetes-models/v1';

export default ({ data }: { data: IIoK8sApiCoreV1ContainerStateRunning }) => {
  return <Space>{data.startedAt && `${data.startedAt}启动`}</Space>;
};
