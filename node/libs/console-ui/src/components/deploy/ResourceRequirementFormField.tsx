import * as React from 'react';
import {
  keyOfResourceRequirement,
  nameOfResourceRequirement,
  ResourceRequirement,
} from '../../apis/service';
import { ProFormSelect } from '@ant-design/pro-components';
import { SantoriniResourceData, useResourcesQuery } from '../../apis/env';
import { Spin } from 'antd';
import { arrayToProSchemaValueEnumMap } from '../../common/ktor';

interface ResourceRequirementFormFieldProps {
  rr: ResourceRequirement;
  envId: string;
}

const ResourceRequirementFormField: React.FC<
  ResourceRequirementFormFieldProps
> = ({ rr, envId }) => {
  const { data: allResources, isLoading } = useResourcesQuery({
    envId,
    params: { type: rr.type },
  });

  if (isLoading || !allResources) {
    return <Spin />;
  }
  return (
    <ProFormSelect
      name={['resourcesSupply', keyOfResourceRequirement(rr)]}
      label={nameOfResourceRequirement(rr)}
      rules={[{ required: true }]}
      valueEnum={arrayToProSchemaValueEnumMap<SantoriniResourceData>(
        (it) => it.name,
        allResources
      )}
    ></ProFormSelect>
  );
};

export default ResourceRequirementFormField;
