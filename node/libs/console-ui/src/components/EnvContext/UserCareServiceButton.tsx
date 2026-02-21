import { ServiceConfigData } from '../../apis/service';
import * as React from 'react';
import { useEnvContext } from '../../layouts/EnvLayout';
import {
  useDeleteServiceCareMutation,
  useQueryServiceCareQuery,
  useTakeServiceCareMutation,
} from '../../apis/userCares';
import { Button, Spin } from 'antd';
import { StarOutlined } from '@ant-design/icons';

interface UserCareServiceButtonProps {
  service: ServiceConfigData;
}

const UserCareServiceButton: React.FC<UserCareServiceButtonProps> = ({
  service: { id: serviceId },
}) => {
  const {
    data: { id: envId },
  } = useEnvContext();
  const p = { serviceId, envId };
  const { data, isLoading } = useQueryServiceCareQuery(p);
  const [takeApi] = useTakeServiceCareMutation();
  const [deleteApi] = useDeleteServiceCareMutation();

  if (isLoading) {
    return <Spin />;
  }
  if (!data) {
    return (
      <Button title={'点击关注'} onClick={() => takeApi(p)}>
        <StarOutlined />
      </Button>
    );
  }
  return (
    <Button title={'点击取消关注'} onClick={() => deleteApi(p)}>
      <span role={'img'} aria-label={'img'}>
        ⭐️
      </span>
    </Button>
  );
};

export default UserCareServiceButton;
