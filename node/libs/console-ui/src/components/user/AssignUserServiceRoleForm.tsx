import * as React from 'react';
import {
  ModalForm,
  ProFormDependency,
  ProFormSelect,
} from '@ant-design/pro-components';
import { Button, Spin } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { io } from '@santorini/generated-santorini-model';
import { kotlinEnumMatch } from '@private-everest/kotlin-utils';
import { useAllServiceQuery } from '../../apis/service';
import {
  arrayToProSchemaValueEnumMap,
  toProSchemaValueEnumMap,
} from '../../common/ktor';
import ServiceUnit from '../service/ServiceUnit';
import {
  useAddServiceRolesMutation,
  useRemoveServiceRolesMutation,
} from '../../apis/user';

interface AssignUserServiceRoleFormProps {
  userId: string;
  /**
   * 已授权的
   */
  roles?: Record<string, io.santorini.model.ServiceRole[]>;
}

const AssignUserServiceRoleForm: React.FC<AssignUserServiceRoleFormProps> = ({
  roles,
  userId,
}) => {
  const [api] = useAddServiceRolesMutation();
  const [, { isLoading: removeApiWorking }] = useRemoveServiceRolesMutation();
  const { data: list } = useAllServiceQuery(undefined);

  if (!list) return <Spin />;
  return (
    <ModalForm
      title={'授权服务角色'}
      trigger={
        <Button type={'dashed'} size={'small'} disabled={removeApiWorking}>
          <PlusOutlined />
        </Button>
      }
      onFinish={async (input) => {
        await api({ ...input, userId } as any).unwrap();
        return true;
      }}
    >
      <ProFormSelect
        name={'serviceId'}
        label={'服务'}
        rules={[{ required: true }]}
        valueEnum={arrayToProSchemaValueEnumMap(
          (it) => it.id,
          list,
          (it) => ({
            text: <ServiceUnit data={it} />,
          })
        )}
      />
      <ProFormDependency name={['serviceId']}>
        {({ serviceId }) => (
          <ProFormSelect
            name={'role'}
            label={'角色'}
            rules={[{ required: true }]}
            valueEnum={toProSchemaValueEnumMap(
              io.santorini.model.ServiceRole.values(),
              (it) => {
                const current = roles?.[serviceId];
                // console.log(
                //   'it:',
                //   it,
                //   ',c:',
                //   serviceId,
                //   ',cc:',
                //   current,
                //   ',ccc:',
                //   current && current.some((c) => kotlinEnumMatch(c, it))
                // );
                // 有 current 并且 2 者匹配
                return {
                  text: it.title,
                  disabled:
                    current && current.some((c) => kotlinEnumMatch(c, it)),
                };
              }
            )}
          />
        )}
      </ProFormDependency>
    </ModalForm>
  );
};

export default AssignUserServiceRoleForm;
