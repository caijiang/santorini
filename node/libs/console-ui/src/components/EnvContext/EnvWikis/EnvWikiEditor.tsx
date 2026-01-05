import {
  ModalForm,
  ModalFormProps,
  ProFormField,
  ProFormSwitch,
  ProFormText,
} from '@ant-design/pro-components';
import { App } from 'antd';
import * as React from 'react';
import { useEnvContext } from '../../../layouts/EnvLayout';
import MDEditor from '@uiw/react-md-editor';
import {
  useCreateEnvWikiMutation,
  useEditEnvWikiMutation,
} from '../../../apis/envWiki';

// 新增 用该 api 可以获取yaml
// 编辑, 可将 IngressPath 转变成 这个玩意儿, 也可以生成编辑的 yaml
/**
 * 支持编辑也支持修改
 * @constructor
 */
const EnvWikiEditor: React.FC<Exclude<ModalFormProps, 'clearOnDestroy'>> = ({
  onFinish,
  ...props
}) => {
  const ec = useEnvContext();
  const { message } = App.useApp();
  const [create] = useCreateEnvWikiMutation();
  const [edit] = useEditEnvWikiMutation();
  // console.log('int:',props.initialValues)

  return (
    <ModalForm
      {...props}
      // clearOnDestroy
      onFinish={async (input) => {
        if (onFinish) {
          return onFinish(input);
        }
        const api = props.initialValues ? edit : create;
        try {
          await api({
            ...input,
            env: ec.data.id,
          } as any).unwrap();
          return true;
        } catch (e) {
          message.error(`更新/创建Wiki失败，原因:${e}`);
          return false;
        }
      }}
    >
      <ProFormText
        name={'title'}
        label={'标题'}
        rules={[{ required: true }]}
        readonly={!!props.initialValues}
      />
      <ProFormSwitch
        name={'global'}
        label={'开放访问'}
        tooltip={'开启后允许所有人访问该wiki'}
      />
      <ProFormField name={'content'} label={'内容'}>
        <MDEditor />
      </ProFormField>
    </ModalForm>
  );
};
export default EnvWikiEditor;
