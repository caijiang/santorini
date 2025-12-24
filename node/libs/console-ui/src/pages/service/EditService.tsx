import { useLocation } from 'react-router-dom';
import {
  ServiceConfigData,
  useUpdateServiceMutation,
} from '../../apis/service';
import { App } from 'antd';
import ServiceForm from './components/ServiceForm';
import { useServiceContext } from '../../layouts/ServiceLayout';
import { useEffect } from 'react';

export default () => {
  const { data, setSharePageContainerProps } = useServiceContext();
  const [api] = useUpdateServiceMutation();
  const { message } = App.useApp();
  const location = useLocation();
  useEffect(() => {
    setSharePageContainerProps({ title: '编辑服务' });
  }, [location]);

  return (
    <ServiceForm
      initialValues={data}
      onFinish={async (input) => {
        const inputData = input as ServiceConfigData;
        await api({ id: data.id, data: inputData }).unwrap();
        message.success(`成功编辑服务-${inputData.name}`);
        return true;
      }}
    />
  );
};
