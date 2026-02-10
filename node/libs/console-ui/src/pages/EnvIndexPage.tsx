import Ingresses from '../components/EnvContext/Ingresses';
import Resources from '../components/EnvContext/Resources';
import {Divider} from 'antd';
import ShareEnv from '../components/EnvContext/ShareEnv';
import EnvWikis from '../components/EnvContext/EnvWikis';
import EnvServices from '../components/EnvContext/EnvServices';
import ServerSideApplyDrawer from '../components/drawer/ServerSideApplyDrawer';

/**
 * 环境首页,展示环境直接相关的资源
 * 1. Ingress
 * 2. 已部署的服务
 */
export default () => {
  return (
    <>
      <ServerSideApplyDrawer />
      <EnvWikis />
      <Divider size={'small'} />
      <ShareEnv />
      <Divider size={'small'} />
      <Ingresses />
      <Divider size={'small'} />
      <Resources />
      <Divider size={'small'} />
      <EnvServices />
    </>
  );
};
