import { io } from '@santorini/generated-santorini-model';
import {
  toInnaNameRule,
  toLabelValueRule,
  toProSchemaValueEnumMap,
} from '../common/ktor';

export const resourceNameColumn = {
  dataIndex: 'name',
  title: '名称',
  colProps: {
    xs: 24,
    sm: 24,
    md: 12,
    lg: 10,
    xl: 5,
    xxl: 4,
  },
  formItemProps: {
    rules: [{ required: true }, { min: 3 }, toInnaNameRule(63)],
  },
};
export const resourceTypeColumn = {
  dataIndex: 'type',
  title: '类型',
  colProps: {
    xs: 12,
    sm: 12,
    md: 6,
    lg: 6,
    xl: 6,
    xxl: 4,
  },
  formItemProps: {
    rules: [{ required: true }],
  },
  valueEnum: toProSchemaValueEnumMap(io.santorini.model.ResourceType.values()),
};
export const resourceDescriptionColumn = {
  dataIndex: 'description',
  title: '描述',
  colProps: {
    xs: 24,
    sm: 24,
    md: 24,
    lg: 20,
    xl: 16,
    xxl: 12,
  },
  formItemProps: {
    rules: [toLabelValueRule()],
  },
};

export const resourceTypeToColumns = (
  type: string | undefined,
  options?: { embedNacosServerAddr?: string }
) => {
  if (!type) {
    return [];
  }
  const rt = io.santorini.model.ResourceType.valueOf(type);
  const fields: readonly io.santorini.model.ResourceFieldDefinition[] =
    rt.fields.asJsReadonlyArrayView();
  console.debug(
    'oe:',
    options?.embedNacosServerAddr,
    'RA:',
    rt === io.santorini.model.ResourceType.NacosAuth,
    ',fields:',
    fields.map((it) => it.name)
  );
  return fields.map((field) => ({
    dataIndex: ['properties', field.name],
    // initialValue:"所以都一样",
    initialValue:
      rt === io.santorini.model.ResourceType.NacosAuth &&
      field.name == 'server-addr'
        ? options?.embedNacosServerAddr
        : undefined,
    title: field.label,
    formItemProps: field.required
      ? {
          rules: [{ required: true }],
        }
      : undefined,
    valueType: !field.secret ? 'text' : 'password',
    tooltip: field.tooltip,
  }));
};
