import Ingresses from '../components/EnvContext/Ingresses';
import Resources from '../components/EnvContext/Resources';
import {Button, Divider} from 'antd';
import {ServiceConfigData} from '../apis/service';
import {toKtorRequest} from '../common/ktor';
import {serviceIdColumn, serviceNameColumn, serviceTypeColumn,} from '../columns/service';
import {useNavigate} from 'react-router-dom';
import {DeploymentUnitOutlined} from '@ant-design/icons';
import EnvChooserModal from '../components/EnvChooserModal';
import {ProTable} from '@ant-design/pro-components';
import {useEnvContext} from '../layouts/EnvLayout';
import ServiceDeployedStatusForEnv from '../components/ServiceDeployedStatusForEnv';
import DashboardLink from '../components/DashboardLink';

/**
 * 环境首页,展示环境直接相关的资源
 * 1. Ingress
 * 2. 已部署的服务
 */
export default () => {
  const nf = useNavigate();
  const {
    data: { id },
  } = useEnvContext();
  return (
    <>
      <Ingresses />
      <Divider size={'small'} />
      <Resources />
      <Divider size={'small'} />
      <ProTable<ServiceConfigData>
        headerTitle={'已部署服务'}
        rowKey={'id'}
        search={false}
        request={toKtorRequest<ServiceConfigData>(`/services?envId=${id}`)}
        columns={[
          serviceIdColumn,
          serviceTypeColumn,
          serviceNameColumn,
          {
            valueType: 'option',
            title: '部署概要',
            render: (_, entity) => (
              <ServiceDeployedStatusForEnv envId={id} service={entity} />
            ),
          },
          {
            valueType: 'option',
            title: '操作',
            render: (_, entity) => [
              // 非生产，发布新版, 生产滚动升级 ？
              // /#/deployment/test-ns/demo-service?namespace=test-ns
              <DashboardLink
                path={`/#/deployment/${id}/${entity.id}?namespace=${id}`}
                key={'dashboard'}
              >
                查看
              </DashboardLink>,
              <EnvChooserModal
                key={'deploy'}
                trigger={
                  <Button title={'部署'} type={'primary'}>
                    <DeploymentUnitOutlined />
                  </Button>
                }
                onChooseEnv={(e) => {
                  nf(`/deploy/${e.id}/${entity.id}`);
                  return true;
                }}
              />,
            ],
          },
        ]}
      />
    </>
  );
};
