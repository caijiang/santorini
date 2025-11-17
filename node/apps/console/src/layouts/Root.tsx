import {Outlet, useLocation} from 'react-router-dom';
import {Suspense} from 'react';
import {ConfigProvider, Skeleton} from 'antd';

export default () => {
  console.info("render Root")
  // ProConfigProvider
  // console.warn("??: ",Outlet)
  const location = useLocation();
  console.log('Current location:', location);
  return <ConfigProvider theme={{cssVar: true, hashed: false}}>
    {/*<RouteDebugger />*/}
    <Suspense fallback={<Skeleton/>}>
      <Outlet/>
    </Suspense>
  </ConfigProvider>
}
