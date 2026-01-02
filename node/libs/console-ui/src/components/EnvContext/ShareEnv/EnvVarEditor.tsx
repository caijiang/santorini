import {
  ModalForm,
  ModalFormProps,
  ProFormSwitch,
  ProFormText,
} from '@ant-design/pro-components';
import { App } from 'antd';
import * as React from 'react';
import { useEnvContext } from '../../../layouts/EnvLayout';
import { useCreateEnvVarMutation } from '../../../apis/env';

interface EnvVarEditor {
  /**
   * 是否开启敏感字段，默认开启
   */
  enableSecret?: false;
}

// 新增 用该 api 可以获取yaml
// 编辑, 可将 IngressPath 转变成 这个玩意儿, 也可以生成编辑的 yaml
/**
 * 支持编辑也支持修改
 * @constructor
 */
const EnvVarEditor: React.FC<
  EnvVarEditor & Exclude<ModalFormProps, 'clearOnDestroy'>
> = ({ onFinish, enableSecret, ...props }) => {
  const ec = useEnvContext();
  const { message } = App.useApp();
  const [api] = useCreateEnvVarMutation();
  // 我们不应该允许编辑 非托管的案例

  // console.log('int:',props.initialValues)
  return (
    <ModalForm
      clearOnDestroy
      onFinish={async (input) => {
        if (onFinish) {
          return onFinish(input);
        }
        try {
          await api({
            env: ec.data.id,
            var: {
              name: input['name']!!,
              secret: input['secret'] == true,
              value: input['value'] ?? '',
            },
          }).unwrap();
          return true;
        } catch (e) {
          message.error(`更新/创建环境变量失败，原因:${e}`);
          return false;
        }
      }}
      {...props}
    >
      <ProFormText
        name={'name'}
        label={'环境变量名称'}
        rules={[
          { required: true },
          {
            pattern: /[A-Z][A-Z0-9_]*/,
            message: '必须大写字母开头,只能包含:数字,大写字母以及下划线',
          },
        ]}
      />
      {enableSecret !== false && (
        <ProFormSwitch name={'secret'} label={'敏感变量'} />
      )}
      <ProFormText name={'value'} label={'环境变量值'} />
    </ModalForm>
  );
};
export default EnvVarEditor;
