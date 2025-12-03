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

const ServiceForm: React.FC<Pick<FormSchema, 'initialValues' | 'onFinish'>> = (
  props
) => {
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
      ]}
      {...props}
    />
  );
};

export default ServiceForm;
