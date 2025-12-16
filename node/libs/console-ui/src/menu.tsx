import { useEnvs } from './hooks/common';
import { PlusSquareFilled, UserOutlined } from '@ant-design/icons';
import { useCurrentLoginUserHaveAnyRole } from './tor/PreAuthorize';

export function useMainNav() {
  const env = useEnvs();
  const users = useCurrentLoginUserHaveAnyRole(['users', 'root']);
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
    users && {
      name: '用户管理',
      icon: <UserOutlined />,
      path: '/users',
    },
    // {
    //   name: 'LoongCollector',
    //   icon: <SwapOutlined />,
    //   path: '/loongCollector',
    // },
  ]
    .filter((it) => !!it)
    .map((it) => it!!);
}
