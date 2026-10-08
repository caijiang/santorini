import {
  ModalForm,
  PageContainer,
  ProFormText,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button, Popconfirm, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useMemo } from 'react';
import {
  useCreateDomainMutation,
  useDeleteDomainMutation,
  useDomainsQuery,
} from '../../apis/easyDomain';
import { toDomainRule } from '../../common/ktor';
import PreAuthorize from '../../tor/PreAuthorize';

interface DomainRow {
  hostname: string;
}

/**
 * `mutation().unwrap()` 拒绝的是一个对象（`FetchBaseQueryError`），
 * 直接塞进模板串只会打出「[object Object]」，看不出任何原因。
 */
const failureReason = (e: unknown) => {
  const err = e as { status?: number | string; data?: unknown } | undefined;
  if (typeof err?.data === 'string' && err.data) return err.data;
  const fromBody = (err?.data as { message?: string } | undefined)?.message;
  if (fromBody) return fromBody;
  return err?.status == null ? String(e) : `HTTP ${err.status}`;
};

/**
 * 域名管理，对应 easy-domain 的 `/domains`。
 *
 * 服务端只认域名：Ingress 按模板渲染，namespace 由服务端环境变量决定，
 * 所以这个页面不属于任何环境（不在 /envFor/:env 下）。
 */
export default () => (
  <PageContainer title={'域名管理'}>
    <PreAuthorize childrenType={'page'} haveAnyRole={['ingress', 'root']}>
      <DomainTable />
    </PreAuthorize>
  </PageContainer>
);

const DomainTable = () => {
  const { message } = App.useApp();
  const { data: domains, isLoading } = useDomainsQuery(undefined);
  const [createApi] = useCreateDomainMutation();

  const [deleteApi] = useDeleteDomainMutation();
  // ProTable 的 record 必须是对象，而接口给的是域名数组
  const dataSource = useMemo<DomainRow[]>(
    () => (domains ?? []).map((hostname) => ({ hostname })),
    [domains]
  );
  return (
    <ProTable<DomainRow>
      rowKey={'hostname'}
      search={false}
      dataSource={dataSource}
      loading={isLoading}
      toolBarRender={() => [
        <ModalForm<{ hostname: string }>
          key={'create'}
          title={'新增域名'}
          trigger={
            <Button type={'primary'} title={'点击新增域名'}>
              <PlusOutlined />
            </Button>
          }
          onFinish={async ({ hostname }) => {
            try {
              await createApi(hostname).unwrap();
              await message.success(`成功添加域名-${hostname}`);
              return true;
            } catch (e) {
              await message.error(`添加域名失败，原因:${failureReason(e)}`);
              return false;
            }
          }}
        >
          <ProFormText
            label={'域名'}
            name={'hostname'}
            rules={[{ required: true }, toDomainRule()]}
            tooltip={
              '只填域名本身，Ingress 由服务端按模板生成；证书会在签发完成后由定时任务同步。'
            }
          />
        </ModalForm>,
      ]}
      columns={[
        {
          valueType: 'index',
          title: '#',
        },
        {
          dataIndex: 'hostname',
          title: '域名',
          render: (_, { hostname }) => (
            <Typography.Text copyable>{hostname}</Typography.Text>
          ),
        },
        {
          valueType: 'option',
          title: '操作',
          render: (_, { hostname }) => [
            <Popconfirm
              key={'delete'}
              title={`确认要删除域名 ${hostname} 及其 Ingress 么`}
              onConfirm={async () => {
                try {
                  await deleteApi(hostname).unwrap();
                } catch (e) {
                  await message.error(`删除域名失败，原因:${failureReason(e)}`);
                }
              }}
            >
              <Button danger size={'small'}>
                <DeleteOutlined />
              </Button>
            </Popconfirm>,
          ],
        },
      ]}
    />
  );
};
