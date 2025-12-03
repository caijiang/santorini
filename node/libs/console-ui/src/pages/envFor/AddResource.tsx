import ResourceForm from '../../components/EnvContext/Resources/ResourceForm';
import { useCreateResourceMutation } from '../../apis/env';
import { useEnvContext } from '../../layouts/EnvLayout';
import { App } from 'antd';

function generateDemoService() {
  const rand = Math.ceil(Math.random() * 1000);
  return {
    name: `demo-resource-${rand}`,
    type: 'Mysql',
    description: 'this-only-test-resource',
    properties: {
      host: 'localhost',
      port: '3306',
      username: 'root',
      password: 'root',
      database: 'db',
    },
  };
}

export default () => {
  const { data: env } = useEnvContext();
  const { message } = App.useApp();
  const [api] = useCreateResourceMutation();
  return (
    <>
      <ResourceForm
        initialValues={import.meta.env.DEV ? generateDemoService() : undefined}
        onFinish={async (data) => {
          await api({ envId: env.id, data }).unwrap();
          message.success(`成功添加资源-${data.name}`);
          return true;
        }}
      />
    </>
  );
};
