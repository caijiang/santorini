import {
  ModalForm,
  ProFormDependency,
  ProFormText,
} from '@ant-design/pro-components';
import { App, Button, Form, Spin } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { toInnaNameRule } from '../common/ktor';
import { useCreateHostMutation, useHostsQuery } from '../apis/host';
import { useMemo } from 'react';
import _ from 'lodash';

export default () => {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [api] = useCreateHostMutation();
  const { data: hosts, isLoading } = useHostsQuery(undefined);
  const defaultName = useMemo(() => {
    if (!hosts) return undefined;
    const x1 = Object.entries(_.countBy(hosts.map((it) => it.issuerName)));
    const x2 = _.maxBy(x1, (x) => x[0]);
    if (x2) return x2[0];
    return undefined;
  }, [hosts]);

  if (isLoading) {
    return <Spin />;
  }
  return (
    <ModalForm
      onFinish={async (x) => {
        try {
          await api(x).unwrap();
          return true;
        } catch (e) {
          message.error(
            '添加域名失败，可能是因为已经存在或者其他字段合法性问题'
          );
          return false;
        }
      }}
      title={'添加域名'}
      form={form}
      trigger={
        <Button>
          <PlusOutlined />
        </Button>
      }
    >
      <ProFormText
        label={'域名'}
        name={'hostname'}
        rules={[{ required: true }]}
      />
      <ProFormDependency name={['hostname']}>
        {({ hostname }) => (
          <ProFormText
            label={'证书存储名称'}
            name={'secretName'}
            rules={[{ required: true }, toInnaNameRule(63)]}
            tooltip={'无需讲究，确保唯一即可，尽可能跟域名有关联'}
            extra={
              <Button
                onClick={() => {
                  if (hostname) {
                    const v1 = (hostname as string).toLowerCase();
                    form.setFieldValue(
                      'secretName',
                      'tls-' + v1.replaceAll('.', '-')
                    );
                  }
                }}
              >
                随便生成
              </Button>
            }
          />
        )}
      </ProFormDependency>
      <ProFormText
        name={'issuerName'}
        label={'签名者'}
        initialValue={defaultName}
        rules={[{ required: true }, toInnaNameRule(63)]}
        tooltip={'一般用缺省的，除非自行创建过其他的签名服务'}
      />
    </ModalForm>
  );
};
