import {ServiceDeployToKubernetesProps} from '../../slices/deployService';
import tommy from './tommy';
import {NginxIngressAnnotation} from '../../components/EnvContext/Ingresses/IngressAnnotation';
import {HostSummary} from '../host';
import {CUEnv} from '../env';
import {IngressPath} from '../../components/EnvContext/Ingresses/df';
import YAML from 'yaml';

/**
 * 很酷的 obj,可以是 json 也可以是 yaml
 */
interface KubFriendlyObject {
  /**
   * json 格式的文本
   */
  toJsonText(): string;

  toJsonObject(): Record<string, any>;

  toYamlText(): string;
}

export function fromYamlTextToObject(yaml: string): KubFriendlyObject {
  const json = YAML.parse(yaml);
  return {
    toJsonText(): string {
      return JSON.stringify(json);
    },
    toJsonObject(): Record<string, any> {
      return json;
    },
    toYamlText(): string {
      return yaml;
    },
  };
}

export function fromJsonToObject(json: Record<string, any>): KubFriendlyObject {
  return {
    toJsonText(): string {
      return JSON.stringify(json);
    },
    toJsonObject(): Record<string, any> {
      return json;
    },
    toYamlText(): string {
      return YAML.stringify(json);
    },
  };
}

/**
 * 服务 yaml,一般分为 deployment 和 service
 */
interface ServiceInstanceYaml {
  deployment: KubFriendlyObject;
  service?: KubFriendlyObject;
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

  serviceInstance: (
    input: ServiceDeployToKubernetesProps
  ) => ServiceInstanceYaml;
}

export default tommy;
// import cdk8s from './cdk8s';
