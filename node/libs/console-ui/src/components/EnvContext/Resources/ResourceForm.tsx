import * as React from 'react';
import { FormSchema } from '@ant-design/pro-form/es/components/SchemaForm/typing';
import { BetaSchemaForm } from '@ant-design/pro-components';
import {
  resourceDescriptionColumn,
  resourceNameColumn,
  resourceTypeColumn,
  resourceTypeToColumns,
} from '../../../columns/env_resource';

const ResourceForm: React.FC<Pick<FormSchema, 'initialValues' | 'onFinish'>> = (
  props
) => {
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
            return resourceTypeToColumns(type);
          },
        },
      ]}
      {...props}
    />
  );
};

export default ResourceForm;
