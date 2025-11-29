import {ServiceDeployToKubernetesProps} from '../../slices/deployService';
import tommy from './tommy';
import {NginxIngressAnnotation} from '../../components/EnvContext/Ingresses/IngressAnnotation';
import {HostSummary} from '../host';
import {CUEnv} from '../env';

/**
 * 服务 yaml,一般分为 deployment 和 service
 */
interface ServiceInstanceYaml {
  deployment: string;
  service?: string;
}

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

  serviceInstance: (
    input: ServiceDeployToKubernetesProps
  ) => ServiceInstanceYaml;
}

export default tommy;
// import cdk8s from './cdk8s';
