import { PageContainer } from '@ant-design/pro-components';
import {
  generateDemoService,
  ServiceConfigData,
  useCreateServiceMutation,
} from '../../apis/service';
import { App } from 'antd';
import ServiceForm from './components/ServiceForm';

// https://github.com/remcohaszing/monaco-yaml/blob/main/examples/vite-example/index.js
export default () => {
  const [create] = useCreateServiceMutation();
  const { message } = App.useApp();

  return (
    <PageContainer title={'添加服务'}>
      <ServiceForm
        initialValues={import.meta.env.DEV ? generateDemoService() : undefined}
        onFinish={async (input) => {
          const inputData = input as ServiceConfigData;
          await create(inputData).unwrap();
          message.success(`成功添加服务-${inputData.name}`);
          return true;
        }}
      />
    </PageContainer>
  );
};
