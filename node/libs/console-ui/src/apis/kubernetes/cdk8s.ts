import { KubernetesYamlGenerator } from './yamlGenerator';
import { App } from 'cdk8s';
import { ServiceChart } from '../../slices/ServiceChart';

export default {
  serviceInstance: (input) => {
    const app = new App({});
    new ServiceChart(
      app,
      input.service.id,
      {
        disableResourceNameHashes: true,
      },
      input
    );
    return {
      deployment: app.synthYaml(),
    };
  },
} as KubernetesYamlGenerator;
