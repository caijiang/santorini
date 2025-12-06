import { PageContainer } from '@ant-design/pro-components';
import { NavLink, useParams } from 'react-router-dom';
import {
  ServiceConfigData,
  useServiceByIdQuery,
  useUpdateServiceMutation,
} from '../../apis/service';
import { App, Button, Result, Skeleton } from 'antd';
import ServiceForm from './components/ServiceForm';

export default () => {
  const { id } = useParams();
  const { data, isLoading } = useServiceByIdQuery(id!!);
  const [api] = useUpdateServiceMutation();
  const { message } = App.useApp();

  if (isLoading) {
    return (
      <PageContainer title={'编辑服务'} loading>
        <Skeleton />
      </PageContainer>
    );
  }
  if (!data) {
    return (
      <PageContainer>
        <Result
          status="404"
          title="404"
          subTitle="Sorry, the page you visited does not exist."
          extra={
            <NavLink to={'/'}>
              <Button type="primary">返回首页</Button>
            </NavLink>
          }
        />
      </PageContainer>
    );
  }
  return (
    <PageContainer title={'编辑服务'}>
      <ServiceForm
        initialValues={data}
        onFinish={async (input) => {
          const inputData = input as ServiceConfigData;
          await api({ id: id!!, data: inputData }).unwrap();
          message.success(`成功编辑服务-${inputData.name}`);
          return true;
        }}
      />
    </PageContainer>
  );
};
