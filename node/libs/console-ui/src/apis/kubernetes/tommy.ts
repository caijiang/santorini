import { CUEditableIngress, KubernetesYamlGenerator } from './yamlGenerator';
import { Deployment } from 'kubernetes-models/apps/v1';
import YAML from 'yaml';
import { Service } from 'kubernetes-models/v1';
import { Ingress } from 'kubernetes-models/networking.k8s.io/v1';
import _ from 'lodash';
import { HostSummary } from '../host';

function makeSure(
  inputs: { [p: string]: string } | undefined,
  fixed: { [p: string]: string }
) {
  if (!inputs) return fixed;
  return {
    ...inputs,
    ...fixed,
  };
}

/**
 *
 * @param inputs 输入
 * @param filter true 则会被移除
 */
function removeByKey(
  inputs: { [p: string]: string } | undefined,
  filter: (key: string) => boolean
):
  | {
      [p: string]: string;
    }
  | undefined {
  if (!inputs) return undefined;
  const list = _.keys(inputs)
    .filter((it) => !filter(it))
    .map((key) => ({
      key,
      value: inputs[key],
    }));
  return _.transform(
    list,
    (obj, it) => {
      obj[it.key] = it.value;
    },
    {} as {
      [key: string]: string;
    }
  );
}

function replaceElement<T>(
  input: T[] | undefined,
  index: number,
  value: T
): T[] {
  if (!input) throw Error(`意图替换数组中原${index} 但数组不存在`);
  if (input.length < index + 1) {
    throw Error(`意图替换数组中原${index} 但数组才${input.length}`);
  }
  const newList = [...input];
  newList[index] = value;
  return newList;
}

function generateIngressAnnotations(
  instance: CUEditableIngress,
  host: HostSummary
) {
  return {
    ..._.transform(
      instance.annotations ?? [],
      (obj, it) => {
        obj[`nginx.ingress.kubernetes.io/${it.name}`] = it.value;
      },
      {} as {
        [key: string]: string;
      }
    ),
    'cert-manager.io/cluster-issuer': host.issuerName!!,
  };
}

function generateIngressPath(instance: CUEditableIngress) {
  return {
    path: instance.path,
    pathType: instance.pathType,
    backend: {
      service: {
        name: instance.backend[0],
        port: {
          number: _.isNumber(instance.backend[1])
            ? instance.backend[1]
            : undefined,
          name: _.isString(instance.backend[1])
            ? instance.backend[1]
            : undefined,
        },
      },
    },
  };
}

export default {
  createIngress: (env, instance, hosts) => {
    const host = hosts.find((it) => it.hostname == instance.host)!!;
    const ingress = new Ingress({
      metadata: {
        labels: {
          'santorini.io/manageable': 'true',
        },
        annotations: generateIngressAnnotations(instance, host),
        generateName: 'santorini-ingress',
        namespace: env.id,
      },
      spec: {
        ingressClassName: 'nginx',
        tls: [
          {
            hosts: [instance.host],
            secretName: host.secretName,
          },
        ],
        rules: [
          {
            host: instance.host,
            http: {
              paths: [generateIngressPath(instance)],
            },
          },
        ],
      },
    });
    return YAML.stringify(ingress.toJSON());
  },
  editIngress: (current, env, instance, hosts) => {
    const host = hosts.find((it) => it.hostname == instance.host)!!;
    const oldJson = current.instance;
    const ingress = new Ingress({
      metadata: {
        ...oldJson.metadata,
        labels: makeSure(oldJson.metadata?.labels, {
          'santorini.io/manageable': 'true',
        }),
        annotations: makeSure(
          removeByKey(oldJson.metadata?.annotations, (it) =>
            it.startsWith('nginx.ingress.kubernetes.io')
          ),
          generateIngressAnnotations(instance, host)
        ),
        namespace: env.id,
      },
      spec: {
        ...oldJson.spec,
        tls: [
          {
            hosts: [instance.host],
            secretName: host.secretName,
          },
        ],
        rules: replaceElement(oldJson.spec?.rules, current.ruleIndex, {
          host: instance.host,
          http: {
            paths: replaceElement(
              oldJson.spec?.rules?.[current.ruleIndex]?.http?.paths,
              current.pathIndex,
              generateIngressPath(instance)
            ),
          },
        }),
      },
    });
    return YAML.stringify(ingress.toJSON());
  },
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
                  // readOnlyRootFilesystem: true,
                  // runAsNonRoot: true,
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
    const s =
      service.ports && service.ports.length > 0
        ? new Service({
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
          })
        : undefined;
    const deploymentJson = deployment.toJSON();
    const serviceJson = s?.toJSON();
    console.log('json:', deploymentJson, ',type:', typeof deploymentJson);
    return {
      deployment: YAML.stringify(deploymentJson),
      service: serviceJson ? YAML.stringify(serviceJson) : undefined,
    };
  },
} as KubernetesYamlGenerator;
