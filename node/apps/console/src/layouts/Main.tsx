import { ProLayout } from '@ant-design/pro-components';
import { Suspense } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { Skeleton } from 'antd';
import modules from '../app/modules';
import {
  useCurrentLoginUserQuery,
  useMainMenu,
  userMenu,
} from '@private-everest/app-support';
import { common } from '@private-everest/generated-common';
import logo from '../assets/logo.svg';

export default () => {
  const location = useLocation();
  const r = useMainMenu(modules);
  const { data: currentUser } = useCurrentLoginUserQuery(undefined);
  return (
    <ProLayout
      title="超级平台"
      siderWidth={135}
      logo={logo}
      breadcrumbRender={(items) => {
        if (!items) return items;
        if (items.length <= 0 || items[0].title !== '首页') {
          //
          return [
            {
              title: '首页',
              path: '/',
            },
            ...items,
          ];
        }
        return items;
      }}
      location={location}
      route={{
        children: [
          {
            name: '首页',
            path: '/',
          },
          ...r,
        ],
      }}
      menuItemRender={(menuItemProps, defaultDom) => {
        if (menuItemProps.isUrl || menuItemProps.children) {
          return defaultDom;
        }
        if (menuItemProps.path && location.pathname !== menuItemProps.path) {
          return (
            <Link
              to={menuItemProps.path}
              target={menuItemProps.target}
              // onClick={() => {
              //   history.push(menuItemProps.path); // 添加路由跳转逻辑
              // }}
            >
              {defaultDom}
            </Link>
          );
        }
        return defaultDom;
      }}
      layout={'mix'}
      avatarProps={
        currentUser && {
          src: common.LoginUserUtils.noNullAvatarUrl(currentUser),
          render: userMenu,
          title: currentUser.name,
        }
      }
      waterMarkProps={
        currentUser && {
          content: currentUser.name,
        }
      }
    >
      <Suspense fallback={<Skeleton />}>
        <Outlet />
      </Suspense>
    </ProLayout>
  );
};
