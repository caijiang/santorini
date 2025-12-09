import { useEnvs } from './hooks/common';
import { PlusSquareFilled } from '@ant-design/icons';

export function useMainNav() {
  const env = useEnvs();
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
    // {
    //   name: 'LoongCollector',
    //   icon: <SwapOutlined />,
    //   path: '/loongCollector',
    // },
  ];
}
