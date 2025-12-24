import DeploymentHistory from '../../components/share/DeploymentHistory';
import { useServiceContext } from '../../layouts/ServiceLayout';
import { useLocation } from 'react-router-dom';
import { useEffect } from 'react';

export default () => {
  const { data, setSharePageContainerProps } = useServiceContext();
  const location = useLocation();
  useEffect(() => {
    setSharePageContainerProps({ title: '服务发布历史' });
  }, [location]);

  return <DeploymentHistory serviceId={data.id} />;
};
