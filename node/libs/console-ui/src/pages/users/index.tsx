import { PageContainer, ProTable } from '@ant-design/pro-components';
import PreAuthorize, {
  useCurrentLoginUserHaveAnyRole,
} from '../../tor/PreAuthorize';
import { toKtorRequest } from '../../common/ktor';
import { User } from '../../c_types';
import UserEnvs from '../../components/user/UserEnvs';

export default () => {
  const envsGranted = useCurrentLoginUserHaveAnyRole(['envs', 'root']);
  // envs 授权环境
  return (
    <PageContainer>
      <PreAuthorize haveAnyRole={['users', 'root']}>
        <ProTable
          search={false}
          rowKey={'id'}
          request={toKtorRequest<User>('/users')}
          columns={[
            {
              dataIndex: 'id',
              title: 'ID',
            },
            {
              dataIndex: 'avatarUrl',
              valueType: 'avatar',
            },
            {
              dataIndex: 'name',
              title: '名字',
            },
            {
              dataIndex: 'createTime',
              title: '创建时间',
              valueType: 'dateTime',
            },
            envsGranted
              ? {
                  title: '环境',
                  render: (_: any, data: User) => <UserEnvs userId={data.id} />,
                }
              : undefined,
          ]
            .filter((it) => !!it)
            .map((it) => it!!)}
        ></ProTable>
      </PreAuthorize>
    </PageContainer>
  );
};
