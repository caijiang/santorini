import {ServiceDeployToKubernetesProps} from '../../slices/deployService';
import tommy from './tommy';

/**
 * 服务 yaml,一般分为 deployment 和 service
 */
interface ServiceInstanceYaml {
  deployment: string;
  service?: string;
}

export interface KubernetesYamlGenerator {
  serviceInstance: (
    input: ServiceDeployToKubernetesProps
  ) => ServiceInstanceYaml;
}

export default tommy;
// import cdk8s from './cdk8s';
