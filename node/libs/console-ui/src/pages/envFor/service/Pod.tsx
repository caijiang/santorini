import { usePodsQuery } from '../../../apis/kubernetes/pods';
import { useParams } from 'react-router-dom';
import { Skeleton } from 'antd';
import { Redirect } from '@private-everest/app-support';
import Pod from '../../../components/kubernetes/tommy/Pod';

export default () => {
  const { env, serviceId, podId } = useParams();
  const { data, isLoading } = usePodsQuery({
    namespace: env,
    name: serviceId,
  });
  const pod = data?.find((it) => it.metadata?.name == podId);

  if (isLoading || !data) {
    return <Skeleton />;
  }
  // 如果 pod 不存在，那么让它回到首页
  if (!pod) {
    return <Redirect to={`/envFor/${env}/services/${serviceId}/pods`} />;
  }
  // 最后状态
  return (
    <>
      <Pod data={pod} />
    </>
  );
};
