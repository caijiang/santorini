import * as React from 'react';
import { useMemo } from 'react';
import { ServiceConfigData } from '../../apis/service';
import {
  useRemoveServiceRolesMutation,
  useUserServiceRolesQuery,
} from '../../apis/user';
import { Space, Spin, Tag } from 'antd';
import { io } from '@santorini/generated-santorini-model';
import { useCurrentLoginUserHaveAnyRole } from '../../tor/PreAuthorize';
import {
  kotlinEnumMatch,
  kotlinEnumToString,
} from '@private-everest/kotlin-utils';

interface ServiceRolesProps {
  userId: string;
  service: ServiceConfigData;
}

const ServiceRoles: React.FC<ServiceRolesProps> = ({ userId, service }) => {
  const grantAssign = useCurrentLoginUserHaveAnyRole('assign');
  const [api] = useRemoveServiceRolesMutation();
  const { data, isLoading } = useUserServiceRolesQuery(userId);
  const roles = useMemo(() => {
    if (!data) return undefined;
    return data[service.id] ?? [];
  }, [data, service]);

  if (isLoading || !roles) return <Spin />;
  if (roles.length == 0) return undefined;
  return (
    <Space>
      {roles.map((role) => (
        <Tag
          key={kotlinEnumToString(role)}
          closable={grantAssign}
          onClose={async () => {
            await api({ userId, serviceId: service.id, role }).unwrap();
          }}
        >
          {
            io.santorini.model.ServiceRole.values().find((x) =>
              kotlinEnumMatch(role, x)
            )?.title
          }
        </Tag>
      ))}
    </Space>
  );
};

export default ServiceRoles;
