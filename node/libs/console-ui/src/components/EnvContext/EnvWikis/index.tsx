import { App, Button, Empty, Popconfirm, Skeleton } from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  GlobalOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { ProCard, ProList } from '@ant-design/pro-components';
import {
  useDeleteEnvWikiMutation,
  useEnvWikisQuery,
} from '../../../apis/envWiki';
import { useEnvContext } from '../../../layouts/EnvLayout';
import EnvWikiEditor from './EnvWikiEditor';
import { MarkdownHooks } from 'react-markdown';
import remarkGfm from 'remark-gfm';

export default () => {
  const { message } = App.useApp();
  const envId = useEnvContext().data.id;
  const { isLoading, data } = useEnvWikisQuery(envId);
  const [removeApi] = useDeleteEnvWikiMutation();
  return (
    <ProCard
      collapsible
      title={'Wiki'}
      loading={isLoading}
      extra={
        <EnvWikiEditor
          key={'create'}
          title={'新增环境Wiki'}
          trigger={
            <Button title={'点击新增环境Wiki'}>
              <PlusOutlined />
            </Button>
          }
        />
      }
    >
      {data ? (
        data.length == 0 ? (
          <Empty />
        ) : (
          <ProList
            dataSource={data}
            rowKey={'title'}
            metas={{
              title: {
                dataIndex: 'title',
              },
              content: {
                render: (_, e) => (
                  <MarkdownHooks remarkPlugins={[remarkGfm]}>
                    {e.content}
                  </MarkdownHooks>
                ),
              },
              actions: {
                render: (_, e) => [
                  e.global && (
                    <a
                      key={'global'}
                      href={`/gwk/${e.envId}/${encodeURIComponent(e.title)}`}
                      target={'_blank'}
                    >
                      <Button size={'small'}>
                        <GlobalOutlined />
                      </Button>
                    </a>
                  ),
                  <EnvWikiEditor
                    key={'edit'}
                    initialValues={e}
                    title={`编辑Wiki-${e.title}`}
                    trigger={
                      <Button size={'small'}>
                        <EditOutlined />
                      </Button>
                    }
                  />,
                  <Popconfirm
                    key={'delete'}
                    title={'删除了条 wiki将不再可见.'}
                    onConfirm={async () => {
                      try {
                        await removeApi({
                          env: envId,
                          title: e.title,
                        }).unwrap();
                      } catch (e) {
                        message.error(`移除流量失败，原因:${e}`);
                      }
                    }}
                  >
                    <Button danger size={'small'}>
                      <DeleteOutlined />
                    </Button>
                  </Popconfirm>,
                ],
              },
            }}
          ></ProList>
        )
      ) : (
        <Skeleton />
      )}
    </ProCard>
  );
};
