import { PageContainer } from '@ant-design/pro-components';
import { NavLink, Outlet, useOutletContext, useParams } from 'react-router-dom';
import { Button, Result, Skeleton } from 'antd';
import * as React from 'react';
import { Suspense, useState } from 'react';
import { PageContainerProps } from '@ant-design/pro-layout/es/components/PageContainer';
import { ServiceConfigData, useServiceByIdQuery } from '../apis/service';

export default () => {
  const { serviceId } = useParams();
  const { data, isLoading } = useServiceByIdQuery(serviceId!!);
  const [sharePageContainerProps, setSharePageContainerProps] =
    useState<PageContainerProps>();
  // const location = useLocation();
  // useEffect(() => {
  //   setSharePageContainerProps(undefined);
  // }, [location]);

  if (isLoading) {
    return (
      <PageContainer>
        <Skeleton />
      </PageContainer>
    );
  }
  if (!data) {
    // 没找到
    return (
      <PageContainer>
        <Result
          status="404"
          title="404"
          subTitle="Sorry, the page you visited does not exist."
          extra={
            <NavLink to={'/'}>
              <Button type="primary">返回首页</Button>
            </NavLink>
          }
        />
      </PageContainer>
    );
  }
  // console.log('sharePageContainerProps:',sharePageContainerProps)
  return (
    <PageContainer {...sharePageContainerProps}>
      <Suspense fallback={<Skeleton />}>
        <Outlet
          context={
            {
              data,
              // sharePageContainerProps,
              setSharePageContainerProps,
            } satisfies ServiceContext
          }
        />
      </Suspense>
    </PageContainer>
  );
};

/**
 * 一个环境的上下文
 */
interface ServiceContext {
  data: ServiceConfigData;
  // sharePageContainerProps?: PageContainerProps;
  setSharePageContainerProps: React.Dispatch<
    React.SetStateAction<PageContainerProps | undefined>
  >;
}

export function useServiceContext() {
  return useOutletContext<ServiceContext>();
}
