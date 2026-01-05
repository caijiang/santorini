import { useEnvContext } from '../../../layouts/EnvLayout';
import { App, Button, Popconfirm, Typography } from 'antd';
import { ProCard, ProTable } from '@ant-design/pro-components';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import EnvVarEditor from './EnvVarEditor';
import {
  useDeleteEnvVarMutation,
  useShareEnvVarsQuery,
} from '../../../apis/env';

export default () => {
  const { data } = useEnvContext();
  const { message } = App.useApp();
  const [removeApi] = useDeleteEnvVarMutation();
  const { data: vars, isLoading } = useShareEnvVarsQuery(data.id);
  // const { ingresses, reason, hostList } = useIngresses(data);
  return (
    <>
      {/*{reason && <Alert type={'error'} message={reason} />}*/}
      <ProCard
        collapsible
        defaultCollapsed
        title={'共享环境变量'}
        loading={isLoading}
        extra={
          <EnvVarEditor
            key={'create'}
            title={'新增环境变量'}
            trigger={
              <Button title={'点击新增环境变量'}>
                <PlusOutlined />
              </Button>
            }
          />
        }
      >
        <Typography.Paragraph italic>
          在当前环境中所有运行环境都将获得的环境变量
        </Typography.Paragraph>
        <ProTable
          columns={[
            {
              dataIndex: 'name',
              title: '环境变量名称',
              copyable: true,
            },
            {
              dataIndex: 'value',
              tooltip: '敏感字段的内容不会被展示',
              title: '环境变量值',
              copyable: true,
            },
            {
              title: '操作',
              valueType: 'option',
              render: (_, entity) => [
                <Popconfirm
                  key="delete"
                  onConfirm={async () => {
                    try {
                      await removeApi({
                        env: data.id,
                        var: entity,
                      }).unwrap();
                    } catch (e) {
                      message.error(`删除环境变量失败，原因:${e}`);
                    }
                  }}
                  title={`确定要删除${entity.name}环境变量？删除后只有重新部署的应用可以获得新环境变量`}
                >
                  <Button danger size={'small'}>
                    <DeleteOutlined />
                  </Button>
                </Popconfirm>,
              ],
            },
          ]}
          dataSource={vars}
          pagination={false}
          search={false}
          rowKey={'name'}
          options={false}
          cardProps={false}
        />
      </ProCard>
    </>
  );
};
