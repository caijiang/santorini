import { Outlet } from 'react-router-dom';
import { Suspense } from 'react';
import { App, ConfigProvider, Skeleton } from 'antd';

export default () => {
  // ProConfigProvider
  return (
    <ConfigProvider theme={{ cssVar: true, hashed: false }}>
      <App>
        {/*<RouteDebugger />*/}
        <Suspense fallback={<Skeleton />}>
          <Outlet />
        </Suspense>
      </App>
    </ConfigProvider>
  );
};
