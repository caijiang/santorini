import * as React from 'react';
import {
  ModalForm,
  ProFormSwitch,
  ProFormText,
} from '@ant-design/pro-components';
import { Button } from 'antd';
import { CUEnv, useUpdateEnvMutation } from '../apis/env';

interface EnvEditorProps {
  data: CUEnv;
}

const EnvEditor: React.FC<EnvEditorProps> = ({ data }) => {
  const [api] = useUpdateEnvMutation();
  return (
    <ModalForm
      trigger={<Button>编辑</Button>}
      initialValues={data}
      title={`编辑${data.id}`}
      onFinish={async (input) => {
        await api({ ...input, id: data.id }).unwrap();
        return true;
      }}
    >
      <ProFormText
        name={'name'}
        label={'名称'}
        rules={[{ required: true }, { max: 50, min: 3 }]}
      />
      <ProFormSwitch name={'production'} label={'生产'} />
    </ModalForm>
  );
};

export default EnvEditor;
