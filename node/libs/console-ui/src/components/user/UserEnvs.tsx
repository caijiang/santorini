import * as React from 'react';
import {
  useGrantEnvMutation,
  useRemoveEnvMutation,
  useUserEnvsQuery,
} from '../../apis/user';
import { Checkbox, Space, Spin } from 'antd';
import { useCurrentLoginUserHaveAnyRole } from '../../tor/PreAuthorize';
import { useEnvs } from '../../hooks/common';
import Env from '../env/Env';

interface UserEnvsProps {
  userId: string;
}

/**
 * 区分当前用户是否有权限，有的话 是一个个 checkbox,反之则是一个个 tag
 * @param userId
 * @constructor
 */
const UserEnvs: React.FC<UserEnvsProps> = ({ userId }) => {
  const { data, isLoading } = useUserEnvsQuery(userId);
  const [addApi, { isLoading: w1 }] = useGrantEnvMutation();
  const [removeApi, { isLoading: w2 }] = useRemoveEnvMutation();
  const list = useEnvs();
  const envsGranted = useCurrentLoginUserHaveAnyRole(['envs', 'root']);

  if (isLoading || !data || !list) return <Spin />;
  if (envsGranted) {
    return (
      <Space direction={'vertical'}>
        {list.map((it) => (
          <Checkbox
            key={it.id}
            disabled={w1 || w2}
            onChange={async () => {
              if (data.includes(it.id)) {
                await removeApi({ userId, envId: it.id }).unwrap();
              } else {
                await addApi({ userId, envId: it.id }).unwrap();
              }
            }}
            checked={data.includes(it.id)}
          >
            <Env data={it} brief />
          </Checkbox>
        ))}
      </Space>
    );
  }
  return (
    <Space direction={'vertical'}>
      {list
        .filter((it) => data.includes(it.id))
        .map((e) => (
          <Env key={e.id} data={e} brief />
        ))}
    </Space>
  );
};

export default UserEnvs;
