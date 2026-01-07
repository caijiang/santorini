import { ServiceConfigData } from '../../../apis/service';
import * as React from 'react';
import { useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, ButtonProps, Modal, ModalProps } from 'antd';
import { ArrowsAltOutlined } from '@ant-design/icons';
import {
  useCreateHpaMutation,
  useDeleteHpaMutation,
  useEditHpaMutation,
  useHpaQuery,
} from '../../../apis/kubernetes/hpa';
import { useEnvContext } from '../../../layouts/EnvLayout';
import {
  ProForm,
  ProFormDependency,
  ProFormDigitRange,
  ProFormSlider,
  ProFormSwitch,
} from '@ant-design/pro-components';
import {
  HorizontalPodAutoscaler,
  IIoK8sApiAutoscalingV2MetricSpec,
} from 'kubernetes-models/autoscaling/v2';

interface HpaEditorProps {
  service: ServiceConfigData;
  /**
   * 触发器的属性
   */
  triggerProps?: Omit<ButtonProps, 'onClick'>;
  modalProps?: Omit<
    ModalProps,
    'open' | 'loading' | 'onCancel' | 'onOk' | 'okButtonProps'
  >;
}

interface FormData {
  enabledHpa?: boolean;
  formReplicas?: number[];
  cpuUtilization?: number;
  memoryUtilization?: number;
}

function averageUtilizationResource(name: string, averageUtilization: number) {
  return {
    resource: {
      name: name,
      target: {
        averageUtilization,
        type: 'Utilization',
      },
    },
    type: 'Resource',
  };
}

function cpuUtilization(averageUtilization: number | undefined) {
  if (!averageUtilization || averageUtilization < 0) return undefined;
  return averageUtilizationResource('cpu', averageUtilization);
}

function memoryUtilization(averageUtilization: number | undefined) {
  if (!averageUtilization || averageUtilization < 0) return undefined;
  return averageUtilizationResource('memory', averageUtilization);
}

function cpuUtilizationNumber(
  metrics?: IIoK8sApiAutoscalingV2MetricSpec[] | undefined
) {
  return metrics?.find(
    (it) =>
      it.type == 'Resource' &&
      it.resource?.name == 'cpu' &&
      it.resource?.target?.type == 'Utilization'
  )?.resource?.target?.averageUtilization;
}

function memoryUtilizationNumber(
  metrics?: IIoK8sApiAutoscalingV2MetricSpec[] | undefined
) {
  return metrics?.find(
    (it) =>
      it.type == 'Resource' &&
      it.resource?.name == 'memory' &&
      it.resource?.target?.type == 'Utilization'
  )?.resource?.target?.averageUtilization;
}

function toForm(input: HorizontalPodAutoscaler): FormData {
  return {
    enabledHpa: true,
    formReplicas: [input.spec?.minReplicas ?? 1, input.spec?.maxReplicas ?? 10],
    cpuUtilization: cpuUtilizationNumber(input.spec?.metrics),
    memoryUtilization: memoryUtilizationNumber(input.spec?.metrics),
  };
}

function toHorizontalPodAutoscaler(
  input: FormData,
  namespace: string,
  service: ServiceConfigData
) {
  if (!input.enabledHpa) return undefined;
  // 我记得…… 无需 patch
  return new HorizontalPodAutoscaler({
    metadata: {
      name: service.id,
      namespace,
    },
    spec: {
      minReplicas: input.formReplicas?.[0],
      maxReplicas: input.formReplicas?.[1] ?? 10,
      scaleTargetRef: {
        apiVersion: 'apps/v1',
        kind: 'Deployment',
        name: service.id,
      },
      metrics: [
        cpuUtilization(input.cpuUtilization),
        memoryUtilization(input.memoryUtilization),
      ]
        .filter((it) => !!it)
        .map((it) => it!!),
    },
  });
}

// spec:
//   maxReplicas: 10
//   metrics:
//   - resource:
//       name: memory
//       target:
//         averageUtilization: 13
//         type: Utilization
//     type: Resource
//   minReplicas: 1
//   scaleTargetRef:
//     apiVersion: apps/v1
//     kind: Deployment
//     name: lecai-nginx
/**
 * 自己管理是否已经打开的状态，不浪费流量
 * @constructor
 */
const HpaEditor: React.FC<HpaEditorProps> = ({
  triggerProps,
  modalProps,
  service,
}) => {
  const { message } = App.useApp();
  const [deleteApi] = useDeleteHpaMutation();
  const [createApi] = useCreateHpaMutation();
  const [editApi] = useEditHpaMutation();
  const [form] = ProForm.useForm();
  // ProForm.useForm()
  const [open, setOpen] = useState(false);
  const [working, setWorking] = useState(false);
  const {
    data: { id: envId },
  } = useEnvContext();
  const { data: hpa, isLoading: hpaLoading } = useHpaQuery(
    {
      envId: envId,
      serviceId: service.id,
    },
    {
      skip: !open,
    }
  );
  // 获取初始化数据 null 表示还没结束, undefined 表示没有设置
  const initFormData = useMemo(() => {
    if (!open) return null;
    if (hpaLoading) return null;
    if (!hpa) return undefined;
    return toForm(hpa);
  }, [open, hpaLoading]);
  useEffect(() => {
    const to = setTimeout(() => {
      form.resetFields();
    }, 1);
    return () => {
      clearTimeout(to);
    };
  }, [initFormData, form]);
  // console.debug('initFormData:', initFormData);
  return (
    <>
      <Button
        title={'编辑自动伸缩配置'}
        onClick={() => setOpen(true)}
        {...triggerProps}
      >
        <ArrowsAltOutlined />
      </Button>
      <Modal
        destroyOnHidden
        title={'自动伸缩配置'}
        open={open}
        loading={initFormData === null}
        okButtonProps={{
          loading: working,
        }}
        onCancel={() => setOpen(false)}
        onOk={async () => {
          try {
            setWorking(true);
            const s1: FormData = await form.validateFields();
            const s2 = toHorizontalPodAutoscaler(s1, envId, service);
            const targetId = {
              envId,
              serviceId: service.id,
            };
            let successMessage: string | undefined = undefined;
            if (!s2) {
              // 移除
              if (initFormData !== undefined) {
                await deleteApi(targetId).unwrap();
                successMessage = '自动伸缩配置已被移除';
              }
            } else {
              const targetData = {
                ...targetId,
                body: s2,
              };
              const api = initFormData !== undefined ? editApi : createApi;
              await api(targetData).unwrap();
              successMessage =
                initFormData !== undefined
                  ? '自动伸缩配置已被更新'
                  : '成功添加自动伸缩配置';
            }
            setOpen(false);
            if (successMessage) {
              message.success(successMessage);
            }
          } catch (e) {
            const { errorFields } = e as any;
            if (errorFields) {
              return;
              // 验证错误？
            }
            message.error('保存时出错,请联系管理员');
          } finally {
            setWorking(false);
          }
        }}
        {...modalProps}
      >
        <Alert
          type="warning"
          showIcon
          description={'集群会将副本数量自动调整到目标状态'}
        />
        {/*如果未设置，默认指标将设置为 80% 的平均 CPU 利用率。*/}
        <ProForm
          initialValues={initFormData === null ? undefined : initFormData}
          submitter={false}
          form={form}
          loading={working}
        >
          <ProFormSwitch name={'enabledHpa'} label={'启用'} />
          <ProFormDependency name={['enabledHpa']}>
            {({ enabledHpa }) =>
              enabledHpa ? (
                <>
                  <ProFormDigitRange
                    name={'formReplicas'}
                    label={'规模限制'}
                    tooltip={'最小和最大的副本数量'}
                    fieldProps={{
                      min: 1,
                    }}
                    rules={[
                      { required: true, message: '规模限制必须输入' },
                      {
                        validator: (_: any, value: any) => {
                          if (!value || value.length !== 2) {
                            return Promise.reject(
                              new Error('规模限制必须输入')
                            );
                          }
                          const [min, max] = value;
                          if (min === undefined || max === undefined) {
                            return Promise.reject(
                              new Error('规模限制必须输入')
                            );
                          }
                          if (min > max) {
                            return Promise.reject(
                              new Error('最小值不能大于最大值')
                            );
                          }
                          return Promise.resolve();
                        },
                      },
                    ]}
                  />
                  <ProFormSlider
                    name={'cpuUtilization'}
                    label={'平均CPU使用率'}
                  />
                  <ProFormSlider
                    name={'memoryUtilization'}
                    label={'平均内存使用率'}
                  />
                </>
              ) : undefined
            }
          </ProFormDependency>
        </ProForm>
      </Modal>
    </>
  );
};

export default HpaEditor;
