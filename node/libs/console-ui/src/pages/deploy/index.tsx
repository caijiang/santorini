import {
  PageContainer,
  ProForm,
  ProFormDependency,
  ProFormDigitRange,
  ProFormField,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
} from '@ant-design/pro-components';
import { useNavigate, useParams } from 'react-router-dom';
import { useEnv } from '../../hooks/common';
import {
  keyOfResourceRequirement,
  useServiceByIdQuery,
} from '../../apis/service';
import { arrayToProSchemaValueEnumMap } from '../../common/ktor';
import { useDispatch } from 'react-redux';
import {
  deployToKubernetes,
  imageRule,
  toImageRepository,
} from '../../slices/deployService';
import { App, Typography } from 'antd';
import { dispatchAsyncThunkActionThrowIfError } from '../../common/rtk';
import ResourceRequirementFormField from '../../components/deploy/ResourceRequirementFormField';
import { useLastReleaseQuery } from '../../apis/deployment';
import { useDockerConfigJsonSecretNamesQuery } from '../../apis/env';
import EnvironmentVariablesEditor from '../../components/form/EnvironmentVariablesEditor';
import PreAuthorize from '../../tor/PreAuthorize';
import { ExclamationCircleOutlined } from '@ant-design/icons';
import ServerSideApplyDrawer from '../../components/drawer/ServerSideApplyDrawer';

export default () => {
  // 作为一个部署服务的专用页面
  // 介绍这个服务
  const { env: envId, service: serviceId } = useParams();
  const env = useEnv(envId);
  const { data: service, isLoading } = useServiceByIdQuery(serviceId!!);
  // 获取密钥
  const { data: secrets } = useDockerConfigJsonSecretNamesQuery(envId!!);
  // 获取环境资源
  const { data: lastReleaseSummary, isLoading: lastReleaseLoading } =
    useLastReleaseQuery({
      envId: envId!!,
      serviceId: serviceId!!,
    });
  const dispatch = useDispatch();
  const { message } = App.useApp();
  const nf = useNavigate();
  const allReady = !lastReleaseLoading && !isLoading && service && secrets;
  return (
    <PageContainer title={'部署'} loading={!allReady}>
      <ServerSideApplyDrawer />
      {!lastReleaseLoading && (
        <ProForm
          initialValues={
            lastReleaseSummary && {
              ...lastReleaseSummary,
              image:
                lastReleaseSummary.imageRepository +
                (lastReleaseSummary.imageTag
                  ? `:${lastReleaseSummary.imageTag}`
                  : ''),
              cpu: [
                lastReleaseSummary.resources.cpu.requestMillis,
                lastReleaseSummary.resources.cpu.limitMillis,
              ],
              memory: [
                lastReleaseSummary.resources.memory.requestMiB,
                lastReleaseSummary.resources.memory.limitMiB,
              ],
            }
          }
          onFinish={async ({ image, ...other }) => {
            const st = toImageRepository(image as string);
            const cpu = other.cpu as number[];
            const memory = other.memory as number[];
            const action = deployToKubernetes({
              service: service!!,
              env: env!!,
              deployData: {
                ...other,
                resources: {
                  cpu: {
                    requestMillis: cpu[0],
                    limitMillis: cpu[1],
                  },
                  memory: {
                    requestMiB: memory[0],
                    limitMiB: memory[1],
                  },
                },
                imageRepository: st[0],
                imageTag: st.length > 1 ? st[1] : undefined,
              },
              lastDeploy: lastReleaseSummary,
            });
            try {
              await dispatchAsyncThunkActionThrowIfError(dispatch, action);
              message.success('已成功部署该服务资源');
              nf('/');
              return true;
            } catch (e) {
              message.error(`操作失败:${e}`);
              return false;
            }
          }}
        >
          <PreAuthorize haveAnyRole={['manager', 'root']}>
            <ProFormSwitch
              name={'experiment'}
              tooltip={'允许跳过预检'}
              label={
                <Typography.Text style={{ color: 'red' }}>
                  <ExclamationCircleOutlined />
                  打开专家模式
                </Typography.Text>
              }
            />
          </PreAuthorize>
          <ProFormSelect
            mode={'multiple'}
            name={'pullSecretName'}
            label={'拉取镜像密钥'}
            // tooltip={'tooltip'}
            valueEnum={
              secrets && arrayToProSchemaValueEnumMap((it) => it, secrets)
            }
          />
          <ProFormDependency name={['pullSecretName', 'experiment']}>
            {({ pullSecretName, experiment }) => (
              <ProFormText
                name={'image'}
                label={'部署镜像'}
                rules={
                  experiment
                    ? [{ required: true }]
                    : [
                        { required: true },
                        imageRule(
                          serviceId!!,
                          envId!!,
                          pullSecretName,
                          dispatch
                        ),
                      ]
                }
              />
            )}
          </ProFormDependency>

          <ProFormDigitRange
            name={'cpu'}
            tooltip={
              <a
                target={'_blank'}
                href={
                  'https://kubernetes.io/zh-cn/docs/concepts/configuration/manage-resources-containers/#meaning-of-cpu'
                }
              >
                millis也可以称之为毫核
              </a>
            }
            label={'CPU资源'}
            fieldProps={{
              min: 1,
              suffix: 'Millis',
            }}
            rules={[{ required: true, message: '请输入有效的CPU资源' }]}
          />
          <ProFormDigitRange
            name={'memory'}
            tooltip={
              <a
                target={'_blank'}
                href={
                  'https://kubernetes.io/zh-cn/docs/concepts/configuration/manage-resources-containers/#meaning-of-memory'
                }
              >
                详细参考
              </a>
            }
            label={'内存资源'}
            fieldProps={{
              min: 1,
              suffix: '兆',
            }}
            rules={[{ required: true, message: '请输入有效的内存资源' }]}
          />
          <ProFormField
            name={'environmentVariables'}
            label={'环境变量'}
            tooltip={'具备最高的优先级'}
          >
            <EnvironmentVariablesEditor />
          </ProFormField>

          {service?.requirements?.map((rr) => (
            <ResourceRequirementFormField
              key={keyOfResourceRequirement(rr)}
              rr={rr}
              envId={envId!!}
            />
          ))}
        </ProForm>
      )}
    </PageContainer>
  );
};
