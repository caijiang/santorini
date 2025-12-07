import {
  PageContainer,
  ProForm,
  ProFormSelect,
  ProFormText,
} from '@ant-design/pro-components';
import { useNavigate, useParams } from 'react-router-dom';
import { useEnv } from '../../hooks/common';
import {
  keyOfResourceRequirement,
  useLastReleaseQuery,
  useServiceByIdQuery,
} from '../../apis/service';
import { useSecretByNamespaceQuery } from '../../apis/kubernetes/common';
import { arrayToProSchemaValueEnumMap } from '../../common/ktor';
import { useDispatch } from 'react-redux';
import { deployToKubernetes } from '../../slices/deployService';
import { App } from 'antd';
import { dispatchActionThrowIfError } from '../../common/rtk';
import ResourceRequirementFormField from '../../components/deploy/ResourceRequirementFormField';

export default () => {
  // 作为一个部署服务的专用页面
  // 介绍这个服务
  const { env: envId, service: serviceId } = useParams();
  const env = useEnv(envId);
  const { data: service, isLoading } = useServiceByIdQuery(serviceId!!);
  // 获取密钥
  const { data: secrets } = useSecretByNamespaceQuery(envId!!);
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
              envRelated: {
                ...other,
                imageRepository: st[0],
                imageTag: st.length > 1 ? st[1] : undefined,
              },
            });
            try {
              await dispatchActionThrowIfError(dispatch, action);
              message.success('已成功部署该服务资源');
              nf('/');
              return true;
            } catch (e) {
              message.error(`操作失败:${e}`);
              return false;
            }
          }}
        >
          <ProFormText
            name={'image'}
            label={'部署镜像'}
            rules={[{ required: true }]}
          />
          {/*<ProFormSegmented name={"abc"} />*/}
          <ProFormSelect
            mode={'multiple'}
            name={'pullSecretName'}
            label={'拉取镜像密钥'}
            // tooltip={'tooltip'}
            valueEnum={
              secrets &&
              arrayToProSchemaValueEnumMap((it) => it.metadata?.name!!, secrets)
            }
          />
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
