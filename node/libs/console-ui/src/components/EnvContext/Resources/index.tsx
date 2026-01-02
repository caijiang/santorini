import { ProCard, ProTable } from '@ant-design/pro-components';
import { useEnvContext } from '../../../layouts/EnvLayout';
import {
  useDeleteResourceMutation,
  useResourcesQuery,
} from '../../../apis/env';
import { Button, Popconfirm } from 'antd';
import { DeleteOutlined, EyeOutlined, PlusOutlined } from '@ant-design/icons';
import { NavLink } from 'react-router-dom';
import {
  resourceDescriptionColumn,
  resourceNameColumn,
  resourceTypeColumn,
} from '../../../columns/env_resource';

export default () => {
  // 还有一个 就是所有服务都会加载的 环境公共配置
  const { data: env } = useEnvContext();
  const {
    data: resources,
    isLoading,
    refetch,
  } = useResourcesQuery({ envId: env.id });
  const [deleteApi] = useDeleteResourceMutation();
  return (
    <>
      <ProCard
        collapsible
        defaultCollapsed
        title={'环境资源'}
        extra={
          <NavLink to={`/envFor/${env.id}/addResource`}>
            <Button title={'点击新增资源'}>
              <PlusOutlined />
            </Button>
          </NavLink>
        }
      >
        <ProTable
          dataSource={resources}
          pagination={false}
          search={false}
          rowKey={'name'}
          loading={isLoading}
          request={async () => {
            return await refetch().unwrap();
          }}
          columns={[
            resourceTypeColumn,
            resourceNameColumn,
            resourceDescriptionColumn,
            {
              valueType: 'option',
              title: '操作',
              render: (_, entity) => [
                <Popconfirm
                  key={'delete'}
                  title={'是否要删除该资源？'}
                  onConfirm={async () => {
                    await deleteApi({
                      envId: env.id,
                      name: entity.name,
                    }).unwrap();
                  }}
                >
                  <Button danger size={'small'}>
                    <DeleteOutlined />
                  </Button>
                </Popconfirm>,
                <NavLink
                  key={'detail'}
                  to={`/envFor/${env.id}/resources/${entity.name}`}
                >
                  <Button size={'small'} type={'dashed'}>
                    <EyeOutlined />
                  </Button>
                </NavLink>,
              ],
            },
          ]}
        />
      </ProCard>
    </>
  );
};
