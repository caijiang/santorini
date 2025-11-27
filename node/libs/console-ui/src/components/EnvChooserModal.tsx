import {
  ModalForm,
  ModalFormProps,
  ProFormSelect,
} from '@ant-design/pro-components';
import { useEnvs } from '../hooks/common';
import { Spin } from 'antd';
import * as React from 'react';
import { arrayToProSchemaValueEnumMap } from '../common/ktor';
import Env from './env/Env';
import { CUEnv } from '../apis/env';

interface EnvChooserModalProps {
  /**
   * 其返回值跟 ModalForm onFinish 返回值定义保持一致
   * @param CUEnv
   */
  onChooseEnv: (env: CUEnv) => PromiseLike<boolean> | boolean;
}

const EnvChooserModal: React.FC<
  EnvChooserModalProps & Exclude<ModalFormProps, 'onFinish'>
> = (props) => {
  const envs = useEnvs();
  const { onChooseEnv, ...modalProps } = props;
  if (!envs) {
    return <Spin />;
  }
  return (
    <ModalForm
      title={'选择环境'}
      onFinish={async ({ target }) => {
        const e = envs.find((that) => that.id == target);
        if (e) {
          return onChooseEnv(e);
        }
        return false;
      }}
      {...modalProps}
    >
      <ProFormSelect
        label={'环境'}
        name={'target'}
        valueEnum={arrayToProSchemaValueEnumMap(
          (it) => it.id,
          envs,
          (it) => ({
            text: <Env data={it} />,
          })
        )}
      />
    </ModalForm>
  );
};

export default EnvChooserModal;
