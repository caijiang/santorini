import {
  PageContainer,
  ProForm,
  ProFormList,
  ProFormText,
  ProSkeleton,
} from '@ant-design/pro-components';
import {
  useCreateSystemStringMutation,
  useCustomMenusQuery,
  useDeleteSystemStringMutation,
  useUpdateSystemStringMutation,
} from '../../apis/advanced';

const name = 'customMenu';

export default () => {
  // json 编辑器，结果必须是 array, 有 name 有 path 有 iconName
  const { data: menus, isLoading } = useCustomMenusQuery(undefined);
  const [createApi] = useCreateSystemStringMutation();
  const [editApi] = useUpdateSystemStringMutation();
  const [deleteApi] = useDeleteSystemStringMutation();
  // toInnaNameRule(73)
  return (
    <PageContainer loading={isLoading}>
      {isLoading ? (
        <ProSkeleton type={'descriptions'} />
      ) : (
        <ProForm
          initialValues={{ data: menus }}
          onFinish={async ({ data: newMenus }) => {
            if (newMenus) {
              // 有新的数据
              if (menus) {
                await editApi({
                  name: name,
                  value: JSON.stringify(newMenus),
                }).unwrap();
              } else {
                await createApi({
                  name: name,
                  value: JSON.stringify(newMenus),
                }).unwrap();
              }
            } else if (menus) {
              await deleteApi(name).unwrap();
            }

            return true;
          }}
        >
          <ProFormList name={'data'}>
            <ProFormText
              name={'name'}
              label={'菜单名称'}
              rules={[{ required: true }]}
            />
            <ProFormText
              name={'iconName'}
              label={'图标'}
              tooltip={
                <p>
                  推荐到
                  <a
                    href={'https://ant.design/components/icon'}
                    target={'_blank'}
                  >
                    这里
                  </a>
                  找
                </p>
              }
            />
            <ProFormText
              name={'path'}
              label={'路径'}
              rules={[{ required: true }]}
            />
          </ProFormList>
        </ProForm>
      )}
    </PageContainer>
  );
};
