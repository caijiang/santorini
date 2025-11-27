import { Chart, ChartProps, Size } from 'cdk8s';
import { Construct } from 'constructs';
import * as kplus from 'cdk8s-plus-33';
import { ServiceConfigData } from '../apis/service';
import { ServiceDeployToKubernetesProps } from './deployService';

// apiVersion: apps/v1
// kind: Deployment
// metadata:
//   labels:
//     santorini.io/id: demo
// santorini.io/manageable: "true"
// santorini.io/service-type: JVM
// name: demo-deployment
// namespace: online
// spec:
//   minReadySeconds: 0
// progressDeadlineSeconds: 600
// replicas: 1
// revisionHistoryLimit: 10
// selector:
//   matchLabels:
//     cdk8s.io/metadata.addr: demo-deployment-c864fc1b
// strategy:
//   rollingUpdate:
//     maxSurge: 25%
//     maxUnavailable: 25%
// type: RollingUpdate
// template:
//   metadata:
//     labels:
//       cdk8s.io/metadata.addr: demo-deployment-c864fc1b
// spec:
//   automountServiceAccountToken: false
// containers:
//   - image: myImage
// imagePullPolicy: Always
// name: main
// ports:
//   - containerPort: 80
// name: http
// resources:
//   limits:
//     cpu: 200m
// memory: 64Mi
// requests:
//   cpu: 100m
// memory: 32Mi
// securityContext:
//   allowPrivilegeEscalation: false
// privileged: false
// readOnlyRootFilesystem: true
// runAsNonRoot: true
// dnsPolicy: ClusterFirst
// hostNetwork: false
// restartPolicy: Always
// securityContext:
//   fsGroupChangePolicy: Always
// runAsNonRoot: true
// setHostnameAsFQDN: false
// shareProcessNamespace: false
// terminationGracePeriodSeconds: 30
// ---
//   apiVersion: v1
// kind: Service
// metadata:
//   name: demo-deployment-service
// namespace: online
// spec:
//   externalIPs: []
// ports:
//   - name: http
// port: 80
// targetPort: 80
// selector:
//   cdk8s.io/metadata.addr: demo-deployment-c864fc1b
// type: ClusterIP


export class ServiceChart extends Chart {
  constructor(
    scope: Construct,
    id: string,
    props: ChartProps = {},
    creatorProps: ServiceDeployToKubernetesProps
  ) {
    super(scope, id, props);
    const { service, env } = creatorProps;

    const fd = new kplus.Deployment(this, 'deployment', {
      metadata: {
        namespace: env.id,
        labels: {
          'santorini.io/id': service.id,
          'santorini.io/manageable': 'true',
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

  private toImage({ envRelated }: ServiceDeployToKubernetesProps) {
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
