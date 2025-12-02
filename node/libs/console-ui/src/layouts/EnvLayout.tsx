import { PageContainer } from '@ant-design/pro-components';
import { NavLink, Outlet, useOutletContext, useParams } from 'react-router-dom';
import { CUEnv } from '../apis/env';
import { useEnv } from '../hooks/common';
import { Button, Result, Skeleton } from 'antd';
import { Suspense } from 'react';

export default () => {
  const { env } = useParams();
  const data = useEnv(env);
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
    <PageContainer>
      <Suspense fallback={<Skeleton />}>
        <Outlet context={{ data } satisfies EnvContext} />
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
}

export function useEnvContext() {
  return useOutletContext<EnvContext>();
}
