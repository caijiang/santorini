import {
  PageContainer,
  ProForm,
  ProFormSelect,
  ProFormText,
} from '@ant-design/pro-components';
import { useParams } from 'react-router-dom';
import { useEnv } from '../../hooks/common';
import { useServiceByIdQuery } from '../../apis/service';
import { useSecretByNamespaceQuery } from '../../apis/kubernetes/common';
import { arrayToProSchemaValueEnumMap } from '../../common/ktor';
import { useDispatch } from 'react-redux';
import { deployToKubernetes } from '../../slices/deployService';
import { UnknownAction } from '@reduxjs/toolkit';

export default () => {
  // useKubernetesJWTTokenQuery(undefined);
  // 作为一个部署服务的专用页面
  // 介绍这个服务
  const { env: envId, service: serviceId } = useParams();
  const env = useEnv(envId);
  const { data: service, isLoading } = useServiceByIdQuery(serviceId!!);
  // 获取密钥
  const { data: secrets } = useSecretByNamespaceQuery(envId!!);
  // 获取环境资源
  const dispatch = useDispatch();
  return (
    <PageContainer title={'部署'} loading={!env || isLoading || !service}>
      <ProForm
        onFinish={async ({ pullSecretName, image }) => {
          // console.log(input);
          const st = (image as string).split(':', 2);
          const action = deployToKubernetes({
            service: service!!,
            env: env!!,
            envRelated: {
              pullSecretName,
              imageRepository: st[0],
              imageTag: st.length > 1 ? st[1] : undefined,
            },
          });
          await dispatch(action as unknown as UnknownAction);
          return true;
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
      </ProForm>
    </PageContainer>
  );
};
