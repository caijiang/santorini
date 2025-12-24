import { useLocation, useParams } from 'react-router-dom';
import { useEffect } from 'react';
import { useEnvContext } from '../../../layouts/EnvLayout';
import DeploymentHistory from '../../../components/share/DeploymentHistory';
import { Divider } from 'antd';

export default () => {
  const { data, setSharePageContainerProps } = useEnvContext();
  const { serviceId } = useParams();
  const location = useLocation();
  useEffect(() => {
    setSharePageContainerProps({ title: '服务发布历史' });
  }, [location]);

  return (
    <>
      <Divider />
      <DeploymentHistory envId={data.id} serviceId={serviceId!!} />
    </>
  );
};
