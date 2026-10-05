import { useEnvs } from './hooks/common';
import {
  GlobalOutlined,
  MenuOutlined,
  PlusSquareFilled,
  UserOutlined,
} from '@ant-design/icons';
import { useCurrentLoginUserHaveAnyRole } from './tor/PreAuthorize';
import { lazy, useMemo } from 'react';
import { useCustomMenusQuery } from './apis/advanced';

const IconFromString = lazy(() => import('./tor/IconFromString'));

export function useMainNav() {
  const env = useEnvs();
  const users = useCurrentLoginUserHaveAnyRole(['users', 'root']);
  const root = useCurrentLoginUserHaveAnyRole(['root']);
  // 与 Ingress 同一套权限：域名管理就是流量管理的入口
  const domains = useCurrentLoginUserHaveAnyRole(['ingress', 'root']);
  const { data: menusSrc } = useCustomMenusQuery(undefined);
  const menus = useMemo(() => {
    return menusSrc?.map(({ iconName, ...m }) => ({
      ...m,
      icon: iconName && <IconFromString name={iconName} />,
    }));
  }, [menusSrc]);
  return [
    {
      name: '添加服务',
      icon: <PlusSquareFilled />,
      path: '/addService',
    },
    ...(env ?? []).map((it) => ({
      name: it.name,
      path: `/envFor/${it.id}`,
    })),
    domains && {
      name: '域名管理',
      icon: <GlobalOutlined />,
      path: '/domains',
    },
    users && {
      name: '用户管理',
      icon: <UserOutlined />,
      path: '/users',
    },
    root && {
      name: '自定义菜单',
      icon: <MenuOutlined />,
      path: '/customMenu',
    },
    ...(menus ?? []),
    // {
    //   name: 'LoongCollector',
    //   icon: <SwapOutlined />,
    //   path: '/loongCollector',
    // },
  ]
    .filter((it) => !!it)
    .map((it) => it!!);
}
