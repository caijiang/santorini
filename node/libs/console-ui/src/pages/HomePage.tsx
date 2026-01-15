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
import { DeploymentUnitOutlined, EditOutlined } from '@ant-design/icons';
import Env from '../components/env/Env';
import EnvChooserModal from '../components/EnvChooserModal';
import { NavLink, useNavigate } from 'react-router-dom';
import ServiceDeployedStatus from '../components/service/ServiceDeployedStatus';
import AllResourcesSummary from '../components/kubernetes/resources/AllResourcesSummary';
import { useClusterResourceStatQuery } from '../apis/misc';

export default () => {
  const envs = useEnvs();
  // KubeNamespaceListProps
  const nf = useNavigate();
  const { data } = useClusterResourceStatQuery(undefined, {
    pollingInterval: 60000,
    skipPollingIfUnfocused: true,
    refetchOnFocus: true,
  });
  return (
    <PageContainer title={'首页'}>
      <AllResourcesSummary state={data} />
      {/*https://codesandbox.io/p/sandbox/6chd58?file=%2FApp.tsx%3A43%2C8*/}
      <ProList<CUEnv>
        headerTitle={'环境'}
        rowKey={'id'}
        dataSource={envs}
        loading={!envs}
        showActions="hover"
        style={{ marginBottom: 10, marginTop: 10 }}
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
        request={toKtorRequest<ServiceConfigData>('/services')}
        columns={[
          {
            hideInTable: true,
            hideInForm: true,
            hideInDescriptions: true,
            hideInSetting: true,
            dataIndex: 'keyword',
            title: '关键字',
          },
          serviceIdColumn,
          serviceTypeColumn,
          serviceNameColumn,
          {
            valueType: 'option',
            title: '已部署',
            render: (_, entity) => <ServiceDeployedStatus service={entity} />,
          },
          {
            valueType: 'option',
            title: '操作',
            render: (_, entity) => [
              <NavLink key={'edit'} to={`/services/${entity.id}/edit`}>
                <Button size={'small'}>
                  <EditOutlined />
                </Button>
              </NavLink>,
              <NavLink key={'history'} to={`/services/${entity.id}/history`}>
                历史
              </NavLink>,
              <EnvChooserModal
                key={'deploy'}
                trigger={
                  <Button size={'small'} title={'部署'} type={'primary'}>
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
