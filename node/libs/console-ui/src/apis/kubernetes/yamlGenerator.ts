import { ServiceDeployToKubernetesProps } from '../../slices/deployService';
import tommy from './tommy';
import { NginxIngressAnnotation } from '../../components/EnvContext/Ingresses/IngressAnnotation';
import { HostSummary } from '../host';
import { CUEnv } from '../env';
import { IngressPath } from '../../components/EnvContext/Ingresses/df';

export interface CUEditableIngress {
  host: string;
  pathType: 'Exact' | 'ImplementationSpecific' | 'Prefix';
  path?: string;
  annotations?: NginxIngressAnnotation[];
  backend: [
    string, // service-id
    number | string // port or port name
  ];
}

export interface KubernetesYamlGenerator {
  // 新增流量
  createIngress: (
    env: CUEnv,
    instance: CUEditableIngress,
    hosts: HostSummary[]
  ) => string;
  /**
   * 删除流量
   */
  deleteIngress: (current: IngressPath, env: CUEnv) => string | undefined;
  /**
   * 编辑流量
   */
  editIngress: (
    current: IngressPath,
    env: CUEnv,
    instance: CUEditableIngress,
    hosts: HostSummary[]
  ) => string;

  /**
   * 生成部署物
   * @param input 部署信息
   * @param firstDeploy 是否首次部署
   */
  generateDeployment(
    input: ServiceDeployToKubernetesProps,
    firstDeploy: boolean
  ): any;

  /**
   * 生成服务
   * @param input 部署信息
   * @return  可能为空
   */
  generateService(input: ServiceDeployToKubernetesProps): any;
}

export default tommy;
