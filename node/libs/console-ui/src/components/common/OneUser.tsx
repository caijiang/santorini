import { UserDataSimple } from '../../apis/deployment';
import { Avatar, Tooltip } from 'antd';

interface OneUserProps {
  data: UserDataSimple;
}

export default ({ data: { avatarUrl, name } }: OneUserProps) => {
  return (
    <Tooltip title={name}>
      <Avatar src={avatarUrl} />
    </Tooltip>
  );
};
