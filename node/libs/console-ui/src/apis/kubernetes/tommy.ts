import { KubernetesYamlGenerator } from './yamlGenerator';
import { Deployment } from 'kubernetes-models/apps/v1';
import YAML from 'yaml';
import { Service } from 'kubernetes-models/v1';

export default {
  serviceInstance: ({ service, env, envRelated }) => {
    const deployment = new Deployment({
      metadata: {
        labels: {
          'santorini.io/id': service.id,
          'santorini.io/manageable': 'true',
          'santorini.io/service-type': service.type,
        },
        name: service.id,
        namespace: env.id,
      },
      spec: {
        minReadySeconds: 0,
        progressDeadlineSeconds: 600,
        replicas: 1,
        revisionHistoryLimit: 10,
        selector: {
          matchLabels: {
            'app.kubernetes.io/name': service.id,
            // 'cdk8s.io/metadata.addr': 'demo-deployment-c864fc1b',
          },
        },
        strategy: {
          rollingUpdate: {
            maxSurge: '25%',
            maxUnavailable: '25%',
          },
          type: 'RollingUpdate',
        },
        template: {
          metadata: {
            labels: {
              'app.kubernetes.io/name': service.id,
            },
          },
          spec: {
            automountServiceAccountToken: false,
            containers: [
              {
                name: 'main',
                image:
                  envRelated.imageRepository +
                  (envRelated.imageTag ? `:${envRelated.imageTag}` : ''),
                imagePullPolicy: 'IfNotPresent', //
                ports: service.ports.map((it) => ({
                  containerPort: it.number,
                  name: it.name,
                })),
                resources: {
                  requests: {
                    cpu: service.resources.cpu.requestMillis + 'm',
                    memory: service.resources.memory.requestMiB + 'Mi',
                  },
                  limits: {
                    cpu: service.resources.cpu.limitMillis + 'm',
                    memory: service.resources.memory.limitMiB + 'Mi',
                  },
                },
                securityContext: {
                  allowPrivilegeEscalation: false,
                  privileged: false,
                  readOnlyRootFilesystem: true,
                  runAsNonRoot: true,
                },
                // dnsPolicy: ClusterFirst
                // hostNetwork: false
                restartPolicy: 'Always',
              },
            ],
          },
        },
        // restartPolicy: Always
        // securityContext:
        //   fsGroupChangePolicy: Always
        // runAsNonRoot: true
        // setHostnameAsFQDN: false
        // shareProcessNamespace: false
        // terminationGracePeriodSeconds: 30
      },
    });
    const s = new Service({
      metadata: {
        name: service.id,
        namespace: env.id,
      },
      spec: {
        ports: service.ports.map((it) => ({
          name: it.name,
          port: it.number,
          targetPort: it.number,
        })),
        selector: {
          'app.kubernetes.io/name': service.id,
        },
      },
    });
    const deploymentJson = deployment.toJSON();
    const serviceJson = s.toJSON();
    console.log('json:', deploymentJson, ',type:', typeof deploymentJson);
    return {
      deployment: YAML.stringify(deploymentJson),
      service: YAML.stringify(serviceJson),
    };
  },
} as KubernetesYamlGenerator;
