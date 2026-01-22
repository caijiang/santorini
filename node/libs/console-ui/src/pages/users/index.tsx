import { PageContainer, ProTable } from '@ant-design/pro-components';
import PreAuthorize, {
  useCurrentLoginUserHaveAnyRole,
} from '../../tor/PreAuthorize';
import { toKtorRequest } from '../../common/ktor';
import { User } from '../../c_types';
import UserEnvs from '../../components/user/UserEnvs';
import UserServiceRoles from '../../components/user/UserServiceRoles';
import _ from 'lodash';
import { App, Checkbox } from 'antd';
import { ProCoreActionType } from '@ant-design/pro-utils/es/typing';
import { useUpdateGrantedAuthorityMutation } from '../../apis/user';

export default () => {
  const { message } = App.useApp();
  const envsGranted = useCurrentLoginUserHaveAnyRole(['envs', 'root']);
  const rolesGranted = useCurrentLoginUserHaveAnyRole(['roles', 'root']);
  const assignGranted = useCurrentLoginUserHaveAnyRole(['roles', 'assign']);
  const [grantedAuthorityMutation] = useUpdateGrantedAuthorityMutation();
  // envs 授权环境
  return (
    <PageContainer>
      <PreAuthorize haveAnyRole={['users', 'root']}>
        <ProTable
          search={false}
          rowKey={'id'}
          request={toKtorRequest<User>('/users')}
          columns={[
            // {
            //   dataIndex: 'id',
            //   title: 'ID',
            // },
            {
              // title: 'index',
              valueType: 'index',
            },
            {
              dataIndex: 'avatarUrl',
              valueType: 'avatar',
            },
            // root 或者 分配者 可以修改权限
            ...[{ role: 'ingress', name: '流量管理' }].map(
              ({ role, name }) => ({
                title: name,
                dataIndex: ['grantAuthorities', role],
                render: (
                  current: any,
                  user: User,
                  ___: any,
                  action: ProCoreActionType | undefined
                ) => {
                  // value: false , type of: boolean
                  // console.log('a2:', a2, ', a3:', a3);
                  // assignGranted
                  const currentChecked =
                    _.isBoolean(current) && current === true;
                  return (
                    <Checkbox
                      checked={currentChecked}
                      disabled={!assignGranted}
                      onChange={async ({ target: { checked } }) => {
                        try {
                          await grantedAuthorityMutation({
                            userId: user.id,
                            target: role,
                            targetValue: checked,
                          }).unwrap();
                          action?.reload();
                        } catch (e) {
                          message.error('错误发生');
                        }
                      }}
                    ></Checkbox>
                  );
                },
              })
            ),
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
            rolesGranted
              ? {
                  title: '服务角色',
                  render: (_: any, data: User) => (
                    <UserServiceRoles userId={data.id} />
                  ),
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
