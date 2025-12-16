import * as React from 'react';
import { useCurrentLoginUserHaveAnyRole } from '../../tor/PreAuthorize';
import { useUserServiceRolesQuery } from '../../apis/user';
import { Space, Spin } from 'antd';
import { useAllServiceQuery } from '../../apis/service';
import ServiceUnit from '../service/ServiceUnit';
import _ from 'lodash';
import AssignUserServiceRoleForm from './AssignUserServiceRoleForm';
import ServiceRoles from './ServiceRoles';

interface UserServiceRolesProps {
  userId: string;
}

/**
 * 服务:tags,, 如果存在权限则
 * @param userId
 * @constructor
 */
const UserServiceRoles: React.FC<UserServiceRolesProps> = ({ userId }) => {
  const grantAssign = useCurrentLoginUserHaveAnyRole('assign');
  const { data: roles, isLoading } = useUserServiceRolesQuery(userId);
  const { data: list } = useAllServiceQuery(undefined);

  if (isLoading || !roles || !list) return <Spin />;
  return (
    <Space direction={'vertical'}>
      {list
        .filter((it) => _.keys(roles).includes(it.id))
        .map((e) => (
          <Space key={e.id}>
            <ServiceUnit key={e.id} data={e} />
            <ServiceRoles userId={userId} service={e} />
          </Space>
        ))}
      {grantAssign && (
        <AssignUserServiceRoleForm userId={userId} roles={roles} />
      )}
    </Space>
  );
};

export default UserServiceRoles;
