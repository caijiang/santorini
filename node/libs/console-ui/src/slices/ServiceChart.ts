import { Chart, ChartProps } from 'cdk8s';
import { Construct } from 'constructs';
import * as kplus from 'cdk8s-plus-33';

export class ServiceChart extends Chart {
  constructor(scope: Construct, id: string, props: ChartProps = {}) {
    super(scope, id, props);

    const fd = new kplus.Deployment(this, 'FrontEnds', {
      containers: [
        {
          image: 'node',
          resources: {
            cpu: {
              request: kplus.Cpu.millis(100),
            },
            memory: {},
          },
          ports: [
            {
              number: 8080,
              name: 'http',
            },
          ],
        },
      ],
      replicas: 1,
    });
    fd.exposeViaService({});
  }
}
