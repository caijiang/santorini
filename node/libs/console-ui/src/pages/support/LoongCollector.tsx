import {
  ModalForm,
  PageContainer,
  ProFormText,
} from '@ant-design/pro-components';
import {
  useConfigMapsQuery,
  useCreateConfigMapMutation,
  useCreateNamespaceMutation,
  useCreateSecretsMutation,
  useNamespacesQuery,
  useSecretsQuery,
} from '../../apis/kubernetes/common';
import {
  CheckCircleTwoTone,
  ExclamationCircleTwoTone,
} from '@ant-design/icons';
import { Button, Space } from 'antd';
import _ from 'lodash';
import {
  useCreateDaemonSetMutation,
  useDaemonSetsQuery,
} from '../../apis/kubernetes/service';

function helmBase64Encode(str: string) {
  return btoa(
    new TextEncoder()
      .encode(str)
      .reduce((acc, byte) => acc + String.fromCharCode(byte), '')
  );
}

const LCN = 'loongcollector';
const configName = LCN + '-user-configmap';
const secretName = LCN + '-secret';
const dsName = LCN + '-ds';
export default () => {
  const { data: allNs } = useNamespacesQuery({ labelSelectors: [] });
  const [createNamespaceApi] = useCreateNamespaceMutation();
  const lcNamespace = allNs?.filter((it) => it?.metadata?.name == LCN);
  const namespaceDone = lcNamespace && lcNamespace.length > 0;
  const namespaceFailed = lcNamespace && lcNamespace.length == 0;

  const { data: allConfigs, isLoading: configLoading } = useConfigMapsQuery({
    namespace: LCN,
    labelSelectors: [],
  });
  const [createConfigApi] = useCreateConfigMapMutation();
  const config = allConfigs?.find((it) => it?.metadata?.name == configName);
  const configDone = !!config;
  const configFailed = !configDone && !configLoading;

  const { data: allSecrets, isLoading: secretLoading } = useSecretsQuery({
    namespace: LCN,
    labelSelectors: [],
    name: secretName,
  });
  const secret = allSecrets?.find(() => true);
  const secretDone = !!secret;
  const secretFailed = !secret && !secretLoading;

  const [createSecretApi] = useCreateSecretsMutation();

  const { data: allDs, isLoading: dsLoading } = useDaemonSetsQuery({
    namespace: LCN,
    labelSelectors: [],
    name: dsName,
  });
  const ds = allDs?.find(() => true);
  const dsDone = !!ds;
  const dsFailed = !ds && !dsLoading;
  const [createDaemonSetApi] = useCreateDaemonSetMutation();

  // https://www.alibabacloud.com/help/zh/sls/loongcollector-installation-kubernetes-1?spm=a2c63.p38356.9556232360.668.56bb4e46KFB296
  return (
    <PageContainer content={<p>协助完成 LoongCollector的配置</p>}>
      <ul>
        <li>
          namespace: {lcNamespace?.find(() => true)?.metadata?.name}
          {namespaceDone && (
            <CheckCircleTwoTone twoToneColor={['green', 'white']} />
          )}
          {namespaceFailed && (
            <Space>
              <ExclamationCircleTwoTone twoToneColor={['red', 'white']} />
              <Button
                onClick={async () => {
                  await createNamespaceApi({
                    metadata: {
                      name: LCN,
                    },
                  }).unwrap();
                }}
              >
                执行
              </Button>
            </Space>
          )}
        </li>
        <li>
          config: {configName}
          {configDone && (
            <CheckCircleTwoTone twoToneColor={['green', 'white']} />
          )}
          {configFailed && (
            <Space>
              <ExclamationCircleTwoTone twoToneColor={['red', 'white']} />
              <Button
                onClick={async () => {
                  await createConfigApi({
                    namespace: LCN,
                    data: {
                      metadata: {
                        name: configName,
                      },
                    },
                  }).unwrap();
                }}
              >
                执行
              </Button>
            </Space>
          )}
        </li>
        <li>
          secret(SLS必须用): {secretName}
          {secretDone && (
            <CheckCircleTwoTone twoToneColor={['green', 'white']} />
          )}
          {secretFailed && (
            <Space>
              <ExclamationCircleTwoTone twoToneColor={['red', 'white']} />
              <ModalForm
                title={'输入阿里云 AK/SK'}
                trigger={<Button>执行</Button>}
                onFinish={async (x) => {
                  await createSecretApi({
                    namespace: LCN,
                    name: secretName,
                    data: {
                      metadata: {
                        namespace: LCN,
                        name: secretName,
                      },
                      type: 'Opaque',
                      data: _.mapValues(x, (cv) => helmBase64Encode(cv)),
                    },
                  }).unwrap();
                  return true;
                }}
              >
                <ProFormText
                  name={'access_key_id'}
                  label={'AK'}
                  rules={[{ required: true }]}
                  tooltip={
                    '推荐使用RAM用户的AccessKey，并授予RAM用户AliyunLogFullAccess系统策略权限'
                  }
                />
                <ProFormText.Password
                  name={'access_key'}
                  label={'SK'}
                  rules={[{ required: true }]}
                />
              </ModalForm>
            </Space>
          )}
        </li>
        <li>
          DaemonSet: {dsName}
          {dsDone && <CheckCircleTwoTone twoToneColor={['green', 'white']} />}
          {dsFailed && (
            <Space>
              <ExclamationCircleTwoTone twoToneColor={['red', 'white']} />
              <Button
                onClick={async () => {
                  const labels = {
                    'k8s-app': dsName,
                  };
                  await createDaemonSetApi({
                    namespace: LCN,
                    data: {
                      metadata: {
                        namespace: LCN,
                        name: dsName,
                        labels: labels,
                      },
                      spec: {
                        selector: {
                          matchLabels: labels,
                        },
                        template: {
                          metadata: {
                            labels: labels,
                          },
                          spec: {
                            tolerations: [
                              {
                                operator: 'Exists',
                              },
                            ],
                            containers: [
                              {
                                name: LCN,
                                env: [
                                  {
                                    name: 'ALIYUN_LOG_ENV_TAGS',
                                    value: '_node_name_|_node_ip_',
                                  },
                                ],
                                image: '',
                              },
                            ],
                          },
                        },
                      },
                    },
                  }).unwrap();
                }}
              >
                执行
              </Button>
            </Space>
          )}
        </li>
      </ul>
    </PageContainer>
  );
};
