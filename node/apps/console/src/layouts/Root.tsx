import { Outlet } from 'react-router-dom';
import { Suspense } from 'react';
import { ConfigProvider, Skeleton } from 'antd';

export default () => {
  // ProConfigProvider
  return (
    <ConfigProvider theme={{ cssVar: true, hashed: false }}>
      {/*<RouteDebugger />*/}
      <Suspense fallback={<Skeleton />}>
        <Outlet />
      </Suspense>
    </ConfigProvider>
  );
};
