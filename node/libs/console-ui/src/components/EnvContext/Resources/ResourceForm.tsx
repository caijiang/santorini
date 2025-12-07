import * as React from 'react';
import { FormSchema } from '@ant-design/pro-form/es/components/SchemaForm/typing';
import { BetaSchemaForm } from '@ant-design/pro-components';
import {
  resourceDescriptionColumn,
  resourceNameColumn,
  resourceTypeColumn,
  resourceTypeToColumns,
} from '../../../columns/env_resource';
import { useEmbedNacosServerAddrQuery } from '../../../apis/env';
import { Skeleton } from 'antd';

const ResourceForm: React.FC<Pick<FormSchema, 'initialValues' | 'onFinish'>> = (
  props
) => {
  const { data: embedNacosServerAddr, isLoading } =
    useEmbedNacosServerAddrQuery(undefined);

  if (isLoading) {
    return <Skeleton />;
  }
  return (
    <BetaSchemaForm
      layoutType={'Form'}
      layout={'horizontal'}
      grid
      columns={[
        resourceNameColumn,
        resourceTypeColumn,
        resourceDescriptionColumn,
        {
          valueType: 'dependency',
          name: ['type'],
          columns: ({ type }) => {
            return resourceTypeToColumns(type, { embedNacosServerAddr });
          },
        },
      ]}
      {...props}
    />
  );
};

export default ResourceForm;
