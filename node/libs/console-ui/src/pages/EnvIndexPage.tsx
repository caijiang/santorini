import Ingresses from '../components/EnvContext/Ingresses';
import Resources from '../components/EnvContext/Resources';
import {Divider} from 'antd';
import {ServiceConfigData} from '../apis/service';
import {toKtorRequest} from '../common/ktor';
import {serviceIdColumn, serviceNameColumn, serviceTypeColumn,} from '../columns/service';
import {NavLink} from 'react-router-dom';
import {ProTable} from '@ant-design/pro-components';
import {useEnvContext} from '../layouts/EnvLayout';
import ServiceDeployedStatusForEnv from '../components/ServiceDeployedStatusForEnv';
import DashboardLink from '../components/DashboardLink';
import RocketForm from '../components/EnvContext/rollout/RocketForm';
import ShareEnv from '../components/EnvContext/ShareEnv';
import EnvWikis from '../components/EnvContext/EnvWikis';
import HpaEditor from '../components/EnvContext/hpa/HpaEditor';

/**
 * 环境首页,展示环境直接相关的资源
 * 1. Ingress
 * 2. 已部署的服务
 */
export default () => {
  const {
    data: { id, production },
  } = useEnvContext();
  return (
    <>
      <EnvWikis />
      <Divider size={'small'} />
      <ShareEnv />
      <Divider size={'small'} />
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
                Dashboard
              </DashboardLink>,
              <NavLink
                key={'pods'}
                to={`/envFor/${id}/services/${entity.id}/pods`}
              >
                Pods
              </NavLink>,
              <NavLink
                key={'history'}
                to={`/envFor/${id}/services/${entity.id}/history`}
              >
                历史
              </NavLink>,
              <RocketForm key={'rock'} service={entity}></RocketForm>,
              production && <HpaEditor service={entity} key={'hpa'} />,
            ],
          },
        ]}
      />
    </>
  );
};
