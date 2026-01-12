import { ServiceConfigData } from '../../../apis/service';
import { toKtorRequest } from '../../../common/ktor';
import {
  serviceIdColumn,
  serviceNameColumn,
  serviceTypeColumn,
} from '../../../columns/service';
import ServiceDeployedStatusForEnv from '../../ServiceDeployedStatusForEnv';
import DashboardLink from '../../DashboardLink';
import { NavLink } from 'react-router-dom';
import RocketForm from '../rollout/RocketForm';
import HpaEditor from '../hpa/HpaEditor';
import { ProTable } from '@ant-design/pro-components';
import { useEnvContext } from '../../../layouts/EnvLayout';
import PodsLink from './PodsLink';

export default () => {
  const {
    data: { id, production },
  } = useEnvContext();

  const request = toKtorRequest<ServiceConfigData>(`/services`);
  return (
    <ProTable<ServiceConfigData>
      headerTitle={'已部署服务'}
      rowKey={'id'}
      search={false}
      params={{ envId: id }}
      request={request}
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
            <PodsLink key={'pods'} service={entity} />,
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
  );
};
