import { io } from '@santorini/generated-santorini-model';
import { toInnaNameRule, toProSchemaValueEnumMap } from '../common/ktor';

export const serviceIdColumn = {
  dataIndex: 'id',
  title: '编码',
  colProps: {
    xs: 24,
    sm: 24,
    md: 12,
    lg: 10,
    xl: 5,
    xxl: 4,
  },
  formItemProps: {
    rules: [
      { required: true },
      { min: 3 },
      toInnaNameRule(63 - '.deployment'.length),
    ],
  },
};
export const serviceNameColumn = {
  dataIndex: 'name',
  title: '名称',
  colProps: {
    xs: 24,
    sm: 24,
    md: 12,
    lg: 12,
    xl: 8,
    xxl: 6,
  },
  formItemProps: {
    rules: [{ required: true }, { max: 50 }],
  },
};

export const serviceTypeColumn = {
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
  valueEnum: toProSchemaValueEnumMap(io.santorini.model.ServiceType.values()),
};
