import { Outlet, useNavigate, useParams } from 'react-router-dom';
import { usePodsQuery } from '../../../apis/kubernetes/pods';
import { Empty, Skeleton, Tabs } from 'antd';
import { Redirect } from '@private-everest/app-support';
import { Suspense } from 'react';

export default () => {
  // 要获取 pods ,那么需要获取所有的
  const { env, serviceId, podId } = useParams();
  // useReplicasetQuery({
  //   namespace: env,
  //   name: serviceId,
  // });
  const nf = useNavigate();
  const { data, isLoading } = usePodsQuery({
    namespace: env,
    name: serviceId,
  });
  if (isLoading) {
    return <Skeleton />;
  }
  if (!data || data.length == 0) {
    return <Empty />;
  }
  if (!podId) {
    return (
      <Redirect
        to={`/envFor/${env}/services/${serviceId}/pods/${
          data!![0]?.metadata?.name
        }`}
      />
    );
  }
  return (
    <>
      <Tabs
        activeKey={podId}
        onChange={(it) => nf(it)}
        items={data?.map((it) => ({
          key: it.metadata?.name!!,
          label: it.metadata?.name,
        }))}
      />
      <Suspense fallback={<Skeleton />}>
        <Outlet />
      </Suspense>
    </>
  );
};
