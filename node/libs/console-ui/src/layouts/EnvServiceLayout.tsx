import {Skeleton} from 'antd';
import {Outlet, useParams} from 'react-router-dom';
import {Suspense, useEffect} from 'react';
import {useEnvContext} from './EnvLayout';
import {useServiceByIdQuery} from '../apis/service';
import {ProDescriptions} from '@ant-design/pro-components';
import {serviceNameColumn, serviceTypeColumn} from '../columns/service';

/**
 * 某环境，某服务
 */
export default () => {
  const ec = useEnvContext();
  const { setSharePageContainerProps } = ec;
  const { serviceId } = useParams();
  useEffect(() => {
    setSharePageContainerProps({ title: serviceId });
  }, []);
  const { data, isLoading } = useServiceByIdQuery(serviceId!!);
  // 可以去编辑服务，什么的
  return (
    <>
      <ProDescriptions
        loading={isLoading}
        dataSource={data}
        columns={[serviceNameColumn, serviceTypeColumn]}
      ></ProDescriptions>

      <Suspense fallback={<Skeleton />}>
        <Outlet context={ec} />
      </Suspense>
    </>
  );
};
