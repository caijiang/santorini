import { IngressPath } from './df';
import {
  ModalForm,
  ModalFormProps,
  ProFormCascader,
  ProFormDependency,
  ProFormField,
  ProFormSelect,
  ProFormText,
} from '@ant-design/pro-components';
import { App, Skeleton, Tooltip } from 'antd';
import { useHostsQuery } from '../../../apis/host';
import { arrayToProSchemaValueEnumMap } from '../../../common/ktor';
import AddHostEditor from '../../AddHostEditor';
import NginxIngressAnnotationsInput from './NginxIngressAnnotationsInput';
import {
  serviceApi,
  ServiceConfigData,
  useAllServiceQuery,
  useServiceByIdQuery,
} from '../../../apis/service';
import * as React from 'react';
import { useEffect, useState } from 'react';
import { useDispatch } from 'react-redux';
import { UnknownAction } from '@reduxjs/toolkit';
import { useEnvContext } from '../../../layouts/EnvLayout';
import yamlGenerator, {
  CUEditableIngress,
} from '../../../apis/kubernetes/yamlGenerator';
import {
  useCreateIngressMutation,
  useEditIngressMutation,
} from '../../../apis/kubernetes/ingress';
import { readNginxIngressAnnotations } from './IngressAnnotation';

interface PathEditorProps {
  data?: IngressPath;
}

interface BackendOption {
  value: string;
  label?: React.ReactNode;
  disabled?: boolean;
  children?: {
    value: number | string;
    label?: React.ReactNode;
    isLeaf?: boolean;
  }[];
  // 标记是否为叶子节点，设置了 `loadData` 时有效
  // 设为 `false` 时会强制标记为父节点，即使当前节点没有 children，也会显示展开图标
  isLeaf?: boolean;
}

// 新增 用该 api 可以获取yaml
// 编辑, 可将 IngressPath 转变成 这个玩意儿, 也可以生成编辑的 yaml

function useResolveCUEditableIngress(data?: IngressPath | undefined):
  | {
      instance: CUEditableIngress;
      service: ServiceConfigData;
    }
  | undefined {
  const rule = data?.instance.spec?.rules?.[data?.ruleIndex];
  const paths = rule?.http?.paths;
  const path = paths?.[data?.pathIndex ?? 0];
  const { data: service } = useServiceByIdQuery(
    path?.backend?.service?.name ?? 'NEVER',
    {
      skip: !path?.backend?.service?.name,
    }
  );
  if (!data) return undefined;
  if (!service) return undefined;
  return {
    instance: {
      host: rule!!.host!!,
      pathType: path!!.pathType,
      path: path!!.path,
      annotations: readNginxIngressAnnotations(data.instance),
      backend: [
        service.id,
        service.ports.find(
          (it) =>
            it.number == path?.backend?.service?.port?.number ||
            it.name == path?.backend?.service?.port?.name
        )?.name!!,
      ],
    },
    service,
  };
}

/**
 * 支持编辑也支持修改
 * @constructor
 */
const PathEditor: React.FC<
  PathEditorProps &
    Exclude<ModalFormProps, 'onFinish' | 'initialValues' | 'clearOnDestroy'>
> = ({ data, ...props }) => {
  const { data: env } = useEnvContext();
  const { message } = App.useApp();
  const [createApi] = useCreateIngressMutation();
  const [editApi] = useEditIngressMutation();
  // host 是基于选择
  const dispatch = useDispatch();
  const { data: hosts } = useHostsQuery(undefined);
  const { data: services1 } = useAllServiceQuery(undefined);
  const [backendOptions, setBackendOptions] = useState<BackendOption[]>();
  const currentResult = useResolveCUEditableIngress(data);
  useEffect(() => {
    if (services1) {
      const currentService = currentResult?.service;
      setBackendOptions(
        services1.map((it) => ({
          value: it.id,
          label: it.name, // TODO full service ui?
          isLeaf: false,
          children: currentService?.ports?.map((cp) => ({
            value: cp.name,
            label: cp.name,
            isLeaf: true,
          })),
        }))
      );
    }
  }, [services1, currentResult?.service]);
  // 我们不应该允许编辑 非托管的案例

  const allReady = hosts && backendOptions && (!data || currentResult);
  if (!allReady) {
    return (
      <ModalForm {...props}>
        <Skeleton />
      </ModalForm>
    );
  }
  return (
    <ModalForm
      initialValues={currentResult?.instance}
      onFinish={async (input) => {
        const instance = input as CUEditableIngress;
        if (!data) {
          // 新增模式
          try {
            const yaml = yamlGenerator.createIngress(env, instance, hosts);
            await createApi({
              namespace: env.id,
              yaml,
            }).unwrap();
            return true;
          } catch (e) {
            message.error(`创建流量失败，原因:${e}`);
            return false;
          }
        } else {
          // 编辑模式
          try {
            const yaml = yamlGenerator.editIngress(data, env, instance, hosts);
            await editApi({
              namespace: env.id,
              name: data.instance.metadata?.name,
              yaml,
            }).unwrap();
            return true;
          } catch (e) {
            message.error(`更新流量失败，原因:${e}`);
            return false;
          }
        }
      }}
      {...props}
    >
      <ProFormSelect
        label={'域名'}
        name={'host'}
        rules={[{ required: true }]}
        valueEnum={arrayToProSchemaValueEnumMap((it) => it.hostname, hosts)}
        extra={<AddHostEditor />}
      />
      <ProFormSelect
        label={'匹配类型'}
        name={'pathType'}
        rules={[{ required: true }]}
        valueEnum={{
          Exact: {
            text: <Tooltip title={'与 URL 路径完全匹配。'}>完全匹配</Tooltip>,
          },
          Prefix: {
            text: (
              <Tooltip
                title={
                  '根据按 “/” 拆分的 URL 路径前缀进行匹配。 匹配是按路径元素逐个元素完成。路径元素引用的是路径中由“/”分隔符拆分的标签列表。 如果每个 p 都是请求路径 p 的元素前缀，则请求与路径 p 匹配。 请注意，如果路径的最后一个元素是请求路径中的最后一个元素的子字符串，则匹配不成功 （例如 /foo/bar 匹配 /foo/bar/baz，但不匹配 /foo/barbaz）。'
                }
              >
                前缀匹配
              </Tooltip>
            ),
          },
          ImplementationSpecific: '其他实现',
        }}
      />
      <ProFormDependency name={['pathType']}>
        {({ pathType }) => (
          <ProFormText
            label={'路径'}
            name={'path'}
            rules={[
              { required: pathType != 'ImplementationSpecific' },
              {
                pattern: RegExp(`^/.*$`),
                message: `路径必须以 “/” 开头`,
              },
            ]}
          />
        )}
      </ProFormDependency>
      <ProFormField
        label={'Nginx Annotations'}
        name={'annotations'}
        tooltip={
          <a
            target={'_blank'}
            href={
              'https://kubernetes.github.io/ingress-nginx/user-guide/nginx-configuration/annotations/'
            }
          >
            配置参考手册
          </a>
        }
      >
        <NginxIngressAnnotationsInput />
      </ProFormField>
      <ProFormCascader
        rules={[{ required: true }]}
        label={'承流后端'}
        name={'backend'}
        fieldProps={{
          changeOnSelect: true,
          options: backendOptions,
          loadData: async (input) => {
            // 只能处理一个
            if (input.length == 0) return;
            const one = input[0] as BackendOption;
            if (one.children) return;
            // @ts-ignore
            const detail = (await dispatch(
              // @ts-ignore
              serviceApi.endpoints.serviceById.initiate(
                one.value
              ) as UnknownAction
            ).unwrap()) as ServiceConfigData;
            setBackendOptions(
              backendOptions.map((it) => {
                if (it.value == one.value) {
                  return {
                    ...it,
                    disabled: detail.ports.length == 0,
                    children: detail.ports.map((thatPort) => ({
                      value: thatPort.name,
                      label: thatPort.name,
                      isLeaf: true,
                    })),
                  };
                } else return it;
              })
            );
          },
        }}
      />
    </ModalForm>
  );
};
export default PathEditor;
