import { PageContainer, ProList } from '@ant-design/pro-components';
import { useKubernetesJWTTokenQuery } from '../apis/token';
import { useNamespacesQuery } from '../apis/common';
import { useEnvs } from '../hooks/common';
import { CUEnv } from '../apis/env';
import { Space, Tag } from 'antd';
import EnvEditor from '../components/EnvEditor';

export default () => {
  // const { data:now } = useCurrentLoginUserQuery(undefined);
  const { data: token } = useKubernetesJWTTokenQuery(undefined);
  const { data } = useNamespacesQuery(undefined, {
    refetchOnFocus: true,
  });
  const envs = useEnvs();
  console.log('token:', token, ',data:', data);
  // KubeNamespaceListProps
  return (
    <PageContainer title={'首页'}>
      {/*https://codesandbox.io/p/sandbox/6chd58?file=%2FApp.tsx%3A43%2C8*/}
      <ProList<CUEnv>
        headerTitle={'环境'}
        rowKey={'id'}
        dataSource={envs}
        loading={!envs}
        showActions="hover"
        metas={{
          title: {
            dataIndex: 'name',
          },
          subTitle: {
            render: (_, e) => (
              <Space size={0}>
                <Tag>{e.id}</Tag>{' '}
                {e.production && <Tag color={'red'}>生产</Tag>}
              </Space>
            ),
          },
          actions: {
            render: (_, e) => <EnvEditor data={e} />,
          },
        }}
      ></ProList>
    </PageContainer>
  );
};
