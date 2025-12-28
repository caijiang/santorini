import {
  PageContainer,
  ProForm,
  ProFormDependency,
  ProFormSelect,
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
import { deployToKubernetes, imageRule } from '../../slices/deployService';
import { App } from 'antd';
import { dispatchAsyncThunkActionThrowIfError } from '../../common/rtk';
import ResourceRequirementFormField from '../../components/deploy/ResourceRequirementFormField';
import { useLastReleaseQuery } from '../../apis/deployment';
import { useDockerConfigJsonSecretNamesQuery } from '../../apis/env';

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
            }
          }
          onFinish={async ({ image, ...other }) => {
            const st = (image as string).split(':', 2);
            const action = deployToKubernetes({
              service: service!!,
              env: env!!,
              deployData: {
                ...other,
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
          <ProFormSelect
            mode={'multiple'}
            name={'pullSecretName'}
            label={'拉取镜像密钥'}
            // tooltip={'tooltip'}
            valueEnum={
              secrets && arrayToProSchemaValueEnumMap((it) => it, secrets)
            }
          />
          <ProFormDependency name={['pullSecretName']}>
            {({ pullSecretName }) => (
              <ProFormText
                name={'image'}
                label={'部署镜像'}
                rules={[
                  { required: true },
                  imageRule(
                    serviceId!!,
                    envId!!,
                    pullSecretName,
                    dispatch,
                    (msg) => message.warning(msg)
                  ),
                ]}
              />
            )}
          </ProFormDependency>

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
