import { PageContainer } from '@ant-design/pro-components';
import { useKubernetesJWTTokenQuery } from '../apis/token';
import { useNamespacesQuery } from '../apis/common';

export default () => {
  const { data: token } = useKubernetesJWTTokenQuery(undefined);
  const { data } = useNamespacesQuery(undefined, {
    refetchOnFocus: true,
  });
  console.log('token:', token, ',data:', data);
  // KubeNamespaceListProps
  return (
    <PageContainer title={'综合门户'}>
      <p>显示很多东西</p>
    </PageContainer>
  );
};
