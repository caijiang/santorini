import Ingresses from '../components/EnvContext/Ingresses';

/**
 * 环境首页,展示环境直接相关的资源
 * 1. Ingress
 * 2. 已部署的服务
 */
export default () => {
  return (
    <>
      <Ingresses />
    </>
  );
};
