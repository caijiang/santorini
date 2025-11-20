import * as React from 'react';
import { ModalForm } from '@ant-design/pro-components';
import { Button } from 'antd';
import { CUEnv } from '../apis/env';

interface EnvEditorProps {
  data: CUEnv;
}

const EnvEditor: React.FC<EnvEditorProps> = ({ data }) => {
  // 服务端调整一下，把 POST 修改为 不存在则创建 已存在则 patch
  return (
    <ModalForm
      trigger={<Button>编辑</Button>}
      title={`编辑${data.id}`}
    ></ModalForm>
  );
};

export default EnvEditor;
