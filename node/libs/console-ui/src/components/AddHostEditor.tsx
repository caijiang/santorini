import {
  ModalForm,
  ProFormDependency,
  ProFormText,
} from '@ant-design/pro-components';
import { App, Button, Form, Spin, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { toInnaNameRule } from '../common/ktor';
import { useCreateHostMutation, useHostsQuery } from '../apis/host';
import { useMemo } from 'react';
import _ from 'lodash';
import { useEnvContext } from '../layouts/EnvLayout';

export default () => {
  const {
    data: { id: envId },
  } = useEnvContext();
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [api] = useCreateHostMutation();
  const { data: hosts, isLoading } = useHostsQuery(undefined);
  const defaultName = useMemo(() => {
    if (!hosts) return undefined;
    const x1 = Object.entries(
      _.countBy(
        hosts.filter((it) => !!it.issuerName).map((it) => it.issuerName!!)
      )
    );
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
      <ProFormText
        name={'issuerName'}
        label={'签名者'}
        initialValue={defaultName}
        rules={[toInnaNameRule(63)]}
        tooltip={'若已获得证书则无需签名'}
      />
      <ProFormDependency name={['hostname', 'issuerName']}>
        {({ hostname, issuerName }) => (
          <ProFormText
            label={'证书存储名称'}
            name={'secretName'}
            rules={[
              toInnaNameRule(63),
              issuerName && issuerName.length > 0
                ? { required: true }
                : undefined,
            ].filter((it) => !!it)}
            tooltip={
              <Typography.Paragraph style={{ color: 'white' }}>
                若使用签名服务生成则无需讲究，唯一即可; 若已获取证书则需手工导入
                <Typography.Text style={{ color: 'white' }} code copyable>
                  kubectl create secret tls [证书存储名称] --cert=[证书 pem文件]
                  --key=[证书 key文件] -n {envId}
                </Typography.Text>
              </Typography.Paragraph>
            }
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
    </ModalForm>
  );
};
