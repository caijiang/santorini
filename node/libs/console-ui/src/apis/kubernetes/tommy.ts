import { CUEditableIngress, KubernetesYamlGenerator } from './yamlGenerator';
import { Deployment } from 'kubernetes-models/apps/v1';
import YAML from 'yaml';
import { Service } from 'kubernetes-models/v1';
import { Ingress } from 'kubernetes-models/networking.k8s.io/v1';
import _ from 'lodash';
import { HostSummary } from '../host';
import { ServiceDeployToKubernetesProps } from '../../slices/deployService';

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
  const v1 = _.transform(
    instance.annotations ?? [],
    (obj, it) => {
      obj[`nginx.ingress.kubernetes.io/${it.name}`] = it.value;
    },
    {} as {
      [key: string]: string;
    }
  );
  if (!host.issuerName) {
    return v1;
  }
  return {
    ...v1,
    'cert-manager.io/cluster-issuer': host.issuerName,
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

// noinspection JSUnusedLocalSymbols,ES6ShorthandObjectProperty
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
  deleteIngress: (current, env) => {
    // 有其他 rule
    const oldJson = current.instance;
    const rules = oldJson.spec?.rules;
    if (!rules) return undefined;
    // 有其他 path
    const targetRule = rules[current.ruleIndex];
    const paths = targetRule.http?.paths;
    if (!paths) return undefined;
    const newPaths = [
      ...paths.slice(0, current.pathIndex),
      ...paths.slice(current.pathIndex + 1),
    ];

    const newRules =
      newPaths.length > 0
        ? replaceElement(rules, current.ruleIndex, {
            ...targetRule,
            http: {
              ...targetRule.http,
              paths: newPaths,
            },
          })
        : [
            ...rules.slice(0, current.ruleIndex),
            ...rules.slice(current.ruleIndex + 1),
          ];

    if (newRules.length == 0) return undefined;

    const ingress = new Ingress({
      metadata: {
        ...oldJson.metadata,
        labels: makeSure(oldJson.metadata?.labels, {
          'santorini.io/manageable': 'true',
        }),
        namespace: env.id,
      },
      spec: {
        ...oldJson.spec,
        rules: newRules,
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
  generateDeployment(
    {service, env, deployData}: ServiceDeployToKubernetesProps,
  ): any {
    const podLabels = {
      'app.kubernetes.io/name': service.id,
      'santorini.io/manageable': 'true',
    };
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
        // ...(firstDeploy
        //   ? {
        //       minReadySeconds: 0,
        //       progressDeadlineSeconds: 600,
        //       replicas: 1,
        //       revisionHistoryLimit: 10,
        //     }
        //   : {}),
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
            labels: podLabels,
          },
          spec: {
            tolerations: [
              // 容忍度
              {
                key: 'focus.santorini.io/namespace',
                value: env.id,
                operator: 'Equal',
              },
              {
                key: 'focus.santorini.io/service',
                value: service.id,
                operator: 'Equal',
              },
            ],
            automountServiceAccountToken: false,
            imagePullSecrets: deployData.pullSecretName?.map((it) => ({
              name: it,
            })),
            containers: [
              {
                name: 'main',
                image:
                  deployData.imageRepository +
                  (deployData.imageTag ? `:${deployData.imageTag}` : ''),
                imagePullPolicy: 'IfNotPresent', //
                ports: service.ports.map((it) => ({
                  containerPort: it.number,
                  name: it.name,
                })),
                envFrom: [
                  {
                    configMapRef: {
                      name: 'santorini-env-share',
                      optional: true,
                    },
                  },
                  {
                    secretRef: {
                      name: 'santorini-env-share',
                      optional: true,
                    },
                  },
                  {
                    secretRef: {
                      name: `${service.id}-env`,
                      optional: true,
                    },
                  },
                ],
                env: [
                  {
                    name: 'kubenamespace',
                    valueFrom: {
                      fieldRef: {
                        apiVersion: 'v1',
                        fieldPath: 'metadata.namespace',
                      },
                    },
                  },
                  {
                    name: 'kubename',
                    valueFrom: {
                      fieldRef: {
                        apiVersion: 'v1',
                        fieldPath: 'metadata.name',
                      },
                    },
                  },
                  {
                    name: 'kubeuid',
                    valueFrom: {
                      fieldRef: {
                        apiVersion: 'v1',
                        fieldPath: 'metadata.uid',
                      },
                    },
                  },
                  ...(deployData.environmentVariables
                    ? _.map(deployData.environmentVariables, (value, name) => ({
                        name,
                        value,
                      }))?.filter((it) => it.name !== 'JAVA_OPTS')
                    : // 因为 JVM 的 JAVA_OPTS 是需要叠加的
                      []),
                ],
                resources: {
                  requests: {
                    cpu: deployData.resources.cpu.requestMillis + 'm',
                    memory: deployData.resources.memory.requestMiB + 'Mi',
                  },
                  limits: {
                    cpu: deployData.resources.cpu.limitMillis + 'm',
                    memory: deployData.resources.memory.limitMiB + 'Mi',
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
                // restartPolicy: 'Always',
                readinessProbe: service.lifecycle?.readinessProbe,
                livenessProbe: service.lifecycle?.livenessProbe,
                startupProbe: service.lifecycle?.startupProbe,
                // terminationMessagePath: '/logs/termination-log',
              },
            ],
            terminationGracePeriodSeconds:
              service.lifecycle?.terminationGracePeriodSeconds,
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
    return deployment.toJSON();
  },
  generateService({service, env}: ServiceDeployToKubernetesProps): any {
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
    return s?.toJSON();
  },
} as KubernetesYamlGenerator;
