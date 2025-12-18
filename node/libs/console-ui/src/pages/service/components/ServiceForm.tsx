import {
  serviceIdColumn,
  serviceNameColumn,
  serviceTypeColumn,
} from '../../../columns/service';
import { toInnaNameRule, toProSchemaValueEnumMap } from '../../../common/ktor';
import { BetaSchemaForm } from '@ant-design/pro-components';
import { FormSchema } from '@ant-design/pro-form/es/components/SchemaForm/typing';
import { io } from '@santorini/generated-santorini-model';
import * as React from 'react';
import { useMemo } from 'react';
import { ServiceConfigData } from '../../../apis/service';
import _ from 'lodash';

interface ClassicsHealthCheck {
  range: [number, number];
  port: number;
  path: string;
  disabled: boolean;
}

/**
 * 重新思考了一下，无论是存活还是就绪探针都应该是高频的
 * @param input
 */
function toServerData(input: unknown): Record<string, any> {
  const { hc, lifecycle, ...others } = input as any;
  if (!hc) {
    return input as any;
  }
  const healthCheck = hc as ClassicsHealthCheck;
  if (healthCheck.disabled) {
    return input as any;
  }
  const x = Math.ceil((healthCheck.range[1] - healthCheck.range[0]) / 10);
  return {
    ...others,
    lifecycle: {
      ...lifecycle,
      /**
       * 启动探针先干活，一直到成功启动
       */
      startupProbe: {
        httpGet: {
          path: healthCheck.path,
          port: healthCheck.port,
        },
        initialDelaySeconds: healthCheck.range[0],
        failureThreshold: x,
      },
      readinessProbe: {
        httpGet: {
          path: healthCheck.path,
          port: healthCheck.port,
        },
        initialDelaySeconds: 1,
        periodSeconds: 1,
      },
      livenessProbe: {
        httpGet: {
          path: healthCheck.path,
          port: healthCheck.port,
        },
        initialDelaySeconds: healthCheck.range[1],
        periodSeconds: 1,
      },
    },
  } as ServiceConfigData;
}

function toHealthCheck({
  lifecycle,
}: ServiceConfigData): ClassicsHealthCheck | undefined {
  if (!lifecycle) {
    return undefined;
  }
  const { livenessProbe, readinessProbe, startupProbe } = lifecycle;
  if (!livenessProbe || !readinessProbe || !startupProbe) return undefined;

  if (!_.isEqual(livenessProbe.httpGet, readinessProbe.httpGet)) {
    console.debug('livenessProbe.httpGet not equals to readinessProbe.httpGet');
    return undefined;
  }
  if (!_.isEqual(livenessProbe.httpGet, startupProbe.httpGet)) {
    console.debug('livenessProbe.httpGet not equals to startupProbe.httpGet');
    return undefined;
  }

  if (livenessProbe.httpGet?.host || livenessProbe.httpGet?.scheme) {
    console.debug('定义过 host ,schema');
    return undefined;
  }
  if (!livenessProbe.httpGet?.port) {
    console.debug('完全没有 httpGet');
    return undefined;
  }

  const { failureThreshold, initialDelaySeconds: mn } = startupProbe;
  if (!failureThreshold || !mn) {
    console.debug('启动探针缺少足够的定义');
    return undefined;
  }
  if (
    startupProbe.periodSeconds ||
    startupProbe.successThreshold ||
    startupProbe.timeoutSeconds
  ) {
    console.debug('启动探针定义得太多了');
    return undefined;
  }

  if (
    readinessProbe.periodSeconds != 1 ||
    readinessProbe.initialDelaySeconds != 1
  ) {
    console.debug('就绪探针跟原始不一致');
    return undefined;
  }
  if (
    readinessProbe.failureThreshold ||
    readinessProbe.successThreshold ||
    readinessProbe.timeoutSeconds
  ) {
    console.debug('就绪探针定义太多了');
    return undefined;
  }

  const { initialDelaySeconds: mx, periodSeconds } = livenessProbe;
  if (!mx || periodSeconds != 1) {
    console.debug('存活探针定义不正确');
    return undefined;
  }
  if (
    livenessProbe.timeoutSeconds ||
    livenessProbe.failureThreshold ||
    livenessProbe.successThreshold
  ) {
    console.debug('存活探针定义太多了');
    return undefined;
  }

  return {
    range: [mn, mx],
    path: livenessProbe.httpGet.path,
    port: livenessProbe.httpGet.port,
    disabled: false,
  };
}

const ServiceForm: React.FC<Pick<FormSchema, 'initialValues' | 'onFinish'>> = ({
  initialValues,
  onFinish,
  ...props
}) => {
  // 根据入参 我们还原出来的话，那就 最快就绪时间,最晚就绪时间, port path
  const i2 = useMemo(() => {
    if (!initialValues) return undefined;
    const hc = toHealthCheck(initialValues as ServiceConfigData);
    console.debug('resolver hc:', hc);
    return {
      ...initialValues,
      hc,
    };
  }, [initialValues]);
  return (
    <BetaSchemaForm
      layoutType={'Form'}
      layout={'horizontal'}
      grid
      columns={[
        serviceIdColumn,
        serviceNameColumn,
        serviceTypeColumn,
        {
          valueType: 'group',
          title: 'CPU资源',
          columns: [
            {
              dataIndex: ['resources', 'cpu', 'requestMillis'],
              valueType: 'digit',
              title: '请求值',
              width: '100%',
              colProps: {
                xs: 12,
                sm: 10,
                md: 8,
                lg: 8,
                xl: 6,
                xxl: 6,
              },
              tooltip: (
                <a
                  target={'_blank'}
                  href={
                    'https://kubernetes.io/zh-cn/docs/concepts/configuration/manage-resources-containers/#meaning-of-cpu'
                  }
                >
                  millis也可以称之为毫核
                </a>
              ),
              fieldProps: {
                suffix: 'Millis',
                min: 1,
              },
              formItemProps: {
                rules: [{ required: true }],
              },
            },
            {
              dataIndex: ['resources', 'cpu', 'limitMillis'],
              valueType: 'digit',
              title: '限制值',
              width: '100%',
              colProps: {
                xs: 12,
                sm: 10,
                md: 8,
                lg: 8,
                xl: 6,
                xxl: 6,
              },
              fieldProps: {
                suffix: 'Millis',
                min: 1,
              },
              formItemProps: {
                rules: [{ required: true }],
              },
            },
          ],
        },
        {
          valueType: 'group',
          title: '内存资源',
          columns: [
            {
              dataIndex: ['resources', 'memory', 'requestMiB'],
              valueType: 'digit',
              title: '请求值',
              width: '100%',
              colProps: {
                xs: 12,
                sm: 10,
                md: 8,
                lg: 8,
                xl: 6,
                xxl: 6,
              },
              tooltip: (
                <a
                  target={'_blank'}
                  href={
                    'https://kubernetes.io/zh-cn/docs/concepts/configuration/manage-resources-containers/#meaning-of-memory'
                  }
                >
                  详细参考
                </a>
              ),
              fieldProps: {
                suffix: '兆',
                min: 1,
              },
              formItemProps: {
                rules: [{ required: true }],
              },
            },
            {
              dataIndex: ['resources', 'memory', 'limitMiB'],
              valueType: 'digit',
              title: '限制值',
              width: '100%',
              colProps: {
                xs: 12,
                sm: 10,
                md: 8,
                lg: 8,
                xl: 6,
                xxl: 6,
              },
              fieldProps: {
                suffix: '兆',
                min: 1,
              },
              formItemProps: {
                rules: [{ required: true }],
              },
            },
          ],
        },
        {
          width: 'lg',
          colProps: { span: 24 },
          filled: true,
          title: '服务端口',
          dataIndex: 'ports',
          valueType: 'formList',
          columns: [
            {
              formItemProps: {
                rules: [{ required: true }],
              },
              width: '10rem',
              // @ts-ignore
              copyable: true,
              title: '端口',
              fieldProps: {
                min: 1,
                max: 65536,
              },
              valueType: 'digit',
              dataIndex: 'number',
            },
            {
              formItemProps: {
                rules: [{ required: true }, toInnaNameRule()],
              },
              width: '15rem',
              // @ts-ignore
              copyable: true,
              title: '名称',
              dataIndex: 'name',
            },
          ],
        },
        {
          width: 'lg',
          colProps: { span: 24 },
          filled: true,
          title: '需求资源',
          dataIndex: 'requirements',
          valueType: 'formList',
          columns: [
            {
              formItemProps: {
                rules: [{ required: true }],
              },
              width: '10rem',
              title: '类型',
              valueEnum: toProSchemaValueEnumMap(
                io.santorini.model.ResourceType.values()
              ),
              dataIndex: 'type',
            },
            {
              formItemProps: {
                rules: [toInnaNameRule()],
              },
              width: '15rem',
              // @ts-ignore
              copyable: true,
              tooltip: '请保持缺省，除非需求多个同类型资源',
              title: '名称',
              dataIndex: 'name',
            },
          ],
        },
        {
          valueType: 'group',
          title: '生命周期',
          columns: [
            {
              title: '安全退出时间',
              dataIndex: ['lifecycle', 'terminationGracePeriodSeconds'],
              valueType: 'digit',
              fieldProps: {
                min: 0,
                suffix: '秒',
              },
              tooltip: (
                <>
                  服务实例确认终止前需要多少时间处理未尽流量或者其他资源回收工作，缺省
                  30秒。
                  <a
                    target={'_blank'}
                    href={
                      'https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/#lifecycle'
                    }
                  >
                    详细参考
                  </a>
                </>
              ),
            },
            // 是否进入高级编辑模式
            // {
            //   title: '高级编辑模式',
            //   dataIndex: ['hc', 'disabled'],
            //   valueType: 'switch',
            // },
            {
              valueType: 'dependency',
              name: [['hc', 'disabled']],
              columns: ({ hc }) => {
                // console.log('hc:', hc);
                const classicHealthCheckInputs = [
                  {
                    formItemProps: {
                      rules: [{ required: true }],
                    },
                    width: '10rem',
                    // @ts-ignore
                    copyable: true,
                    tooltip: '用于健康检查',
                    title: 'HTTP GET 端口',
                    fieldProps: {
                      min: 1,
                      max: 65536,
                    },
                    valueType: 'digit' as 'digit',
                    dataIndex: ['hc', 'port'],
                  },
                  {
                    formItemProps: {
                      rules: [{ required: true }],
                    },
                    width: '10rem',
                    // @ts-ignore
                    copyable: true,
                    tooltip: '用于健康检查',
                    title: 'HTTP GET 路径',
                    dataIndex: ['hc', 'path'],
                  },
                  {
                    valueType: 'digitRange' as 'digitRange',
                    dataIndex: ['hc', 'range'],
                    title: '实例启动时间',
                    tooltip: '预估最快和最慢启动时间',
                    fieldProps: {
                      min: 0,
                      suffix: '秒',
                    },
                    formItemProps: {
                      rules: [
                        { required: true },
                        {
                          validator: async (_: any, value: any) => {
                            if (!value) return Promise.resolve();

                            const [min, max] = value;

                            // 允许只填一边
                            if (min == null || max == null) {
                              return Promise.resolve();
                            }

                            if (max - min < 50) {
                              return Promise.reject(
                                new Error('最大值和最小值的差必须 ≥ 50')
                              );
                            }

                            return Promise.resolve();
                          },
                        },
                      ],
                    },
                  },
                ];
                if (!hc) return classicHealthCheckInputs;
                const { disabled } = hc;
                // 默认开启
                if (disabled === true) {
                  return [];
                }
                return classicHealthCheckInputs;
              },
            },
          ],
        },
      ]}
      initialValues={i2}
      onFinish={async (input) => {
        return onFinish?.(toServerData(input));
      }}
      {...props}
    />
  );
};

export default ServiceForm;
