import { PageContainer } from '@ant-design/pro-components';
import {
  NavLink,
  Outlet,
  useLocation,
  useOutletContext,
  useParams,
} from 'react-router-dom';
import { CUEnv } from '../apis/env';
import { useEnv } from '../hooks/common';
import { Button, Result, Skeleton } from 'antd';
import * as React from 'react';
import { Suspense, useEffect, useState } from 'react';
import { PageContainerProps } from '@ant-design/pro-layout/es/components/PageContainer';

export default () => {
  const { env } = useParams();
  const data = useEnv(env);
  const [sharePageContainerProps, setSharePageContainerProps] =
    useState<PageContainerProps>();
  const location = useLocation();
  useEffect(() => {
    setSharePageContainerProps(undefined);
  }, [location]);
  if (data === undefined) {
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
  return (
    <PageContainer {...sharePageContainerProps}>
      <Suspense fallback={<Skeleton />}>
        <Outlet
          context={
            {
              data,
              sharePageContainerProps,
              setSharePageContainerProps,
            } satisfies EnvContext
          }
        />
      </Suspense>
    </PageContainer>
  );
};

/**
 * 一个环境的上下文
 */
interface EnvContext {
  /**
   * 基本环境数据
   */
  data: CUEnv;
  sharePageContainerProps?: PageContainerProps;
  setSharePageContainerProps: React.Dispatch<
    React.SetStateAction<PageContainerProps | undefined>
  >;
}

export function useEnvContext() {
  return useOutletContext<EnvContext>();
}
