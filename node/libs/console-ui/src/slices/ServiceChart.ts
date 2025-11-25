import { Chart, ChartProps, Size } from 'cdk8s';
import { Construct } from 'constructs';
import * as kplus from 'cdk8s-plus-33';
import { EnvRelatedServiceResource, ServiceConfigData } from '../apis/service';
import { CUEnv } from '../apis/env';

export interface ServiceCreatorProps {
  service: ServiceConfigData;
  env: CUEnv;
  envRelated: EnvRelatedServiceResource;
}

export class ServiceChart extends Chart {
  constructor(
    scope: Construct,
    id: string,
    props: ChartProps = {},
    creatorProps: ServiceCreatorProps
  ) {
    super(scope, id, props);
    const { service, env } = creatorProps;

    const fd = new kplus.Deployment(this, 'deployment', {
      metadata: {
        namespace: env.id,
        labels: {
          'santorini.io/service-type': service.type,
        },
      },
      containers: [
        {
          image: this.toImage(creatorProps),
          resources: this.toResource(service),
          ports: this.toPorts(service),
        },
      ],
      replicas: 1,
    });
    fd.exposeViaService({});
  }

  private toImage({ envRelated }: ServiceCreatorProps) {
    if (envRelated.imageTag) {
      return envRelated.imageRepository + ':' + envRelated.imageTag;
    }
    return envRelated.imageRepository;
  }

  private toResource({ resources }: ServiceConfigData) {
    return {
      cpu: {
        request: kplus.Cpu.millis(resources.cpu.requestMillis),
        limit: kplus.Cpu.millis(resources.cpu.limitMillis),
      },
      memory: {
        request: Size.mebibytes(resources.memory.requestMiB),
        limit: Size.mebibytes(resources.memory.limitMiB),
      },
    };
  }

  private toPorts({ ports }: ServiceConfigData) {
    return ports.map((it) => ({
      ...it,
      number: it.number,
      name: it.name,
    }));
  }
}
