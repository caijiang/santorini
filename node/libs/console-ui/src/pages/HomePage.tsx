import { PageContainer, ProList, ProTable } from '@ant-design/pro-components';
import { useEnvs } from '../hooks/common';
import { CUEnv } from '../apis/env';
import { Button } from 'antd';
import EnvEditor from '../components/EnvEditor';
import { toKtorRequest } from '../common/ktor';
import { ServiceConfigData } from '../apis/service';
import {
  serviceIdColumn,
  serviceNameColumn,
  serviceTypeColumn,
} from '../columns/service';
import { DeploymentUnitOutlined } from '@ant-design/icons';
import Env from '../components/env/Env';
import EnvChooserModal from '../components/EnvChooserModal';
import { useNavigate } from 'react-router-dom';

export default () => {
  const envs = useEnvs();
  // KubeNamespaceListProps
  const nf = useNavigate();
  return (
    <PageContainer title={'首页'}>
      {/*https://codesandbox.io/p/sandbox/6chd58?file=%2FApp.tsx%3A43%2C8*/}
      <ProList<CUEnv>
        headerTitle={'环境'}
        rowKey={'id'}
        dataSource={envs}
        loading={!envs}
        showActions="hover"
        style={{ marginBottom: 10 }}
        metas={{
          title: {
            dataIndex: 'name',
          },
          subTitle: {
            render: (_, e) => <Env.SubTitle data={e} />,
          },
          actions: {
            render: (_, e) => <EnvEditor data={e} />,
          },
        }}
      ></ProList>
      <ProTable<ServiceConfigData>
        headerTitle={'服务配置'}
        rowKey={'id'}
        search={false}
        request={toKtorRequest<ServiceConfigData>('/services')}
        columns={[
          serviceIdColumn,
          serviceTypeColumn,
          serviceNameColumn,
          {
            valueType: 'option',
            title: '操作',
            render: (_, entity) => [
              <EnvChooserModal
                key={'deploy'}
                trigger={
                  <Button title={'部署'} type={'primary'}>
                    <DeploymentUnitOutlined />
                  </Button>
                }
                onChooseEnv={(e) => {
                  nf(`/deploy/${e.id}/${entity.id}`);
                  // dispatch(
                  //   startDeployServiceToEnv({
                  //     service: entity.id,
                  //     env: e,
                  //   }) as unknown as UnknownAction
                  // );
                  return true;
                }}
              />,
            ],
          },
        ]}
      />
    </PageContainer>
  );
};
