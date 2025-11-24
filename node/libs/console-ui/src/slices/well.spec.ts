import * as kplus from 'cdk8s-plus-33';
import { Construct } from 'constructs';
import { App, Chart, ChartProps } from 'cdk8s';

export class MyChart extends Chart {
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

const app = new App({});
new MyChart(app, 'deployment', {
  disableResourceNameHashes: true,
});
// chart.addDependency(
//   new kplus.Service(chart, 'Fronend', {
//     ports: [
//       {
//         port: 8080,
//         name: 'http',
//         protocol: Protocol.TCP,
//       },
//     ],
//   })
// );
// app.synth();

describe('SantoriniConsoleUi', () => {
  it('should render successfully', () => {
    // const {baseElement} = render(<SantoriniConsoleUi/>);
    // expect(baseElement).toBeTruthy();
    // @ts-ignore
    const yaml = app.synthYaml();
    // console.info(yaml);

    // const recoverApp = new App();
    // const recoverChart = new Chart(recoverApp, 'chart');
    // ApiObjects
  });
});
