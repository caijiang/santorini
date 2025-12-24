import { RocketOutlined } from '@ant-design/icons';
import { ModalForm, ProFormText } from '@ant-design/pro-components';
import { ServiceConfigData, useServiceByIdQuery } from '../../../apis/service';
import { Alert, App, Button, Spin } from 'antd';
import * as React from 'react';
import { useEnvContext } from '../../../layouts/EnvLayout';
import { deployToKubernetes } from '../../../slices/deployService';
import { dispatchActionThrowIfError } from '../../../common/rtk';
import { useDispatch } from 'react-redux';
import { useLastReleaseQuery } from '../../../apis/deployment';

interface RocketFormProps {
  service: ServiceConfigData;
}

/**
 * 版本更迭
 * @constructor
 */
const RocketForm: React.FC<RocketFormProps> = ({
  service: { id: serviceId },
}) => {
  const { message } = App.useApp();
  const dispatch = useDispatch();
  const { data: env } = useEnvContext();
  const { data: service, isLoading } = useServiceByIdQuery(serviceId);
  const { data: lastReleaseSummary, isLoading: lastReleaseLoading } =
    useLastReleaseQuery({
      envId: env.id,
      serviceId: serviceId,
    });
  if (lastReleaseLoading || isLoading) {
    return <Spin />;
  }
  if (
    !lastReleaseSummary ||
    !lastReleaseSummary.serviceDataSnapshot ||
    !service
  ) {
    return undefined;
  }
  return (
    <ModalForm
      onFinish={async ({ tag }) => {
        const action = deployToKubernetes({
          service: service,
          env: env,
          deployData: {
            ...lastReleaseSummary,
            imageTag: tag,
          },
          lastDeploy: lastReleaseSummary,
        });
        try {
          await dispatchActionThrowIfError(dispatch, action);
          message.success('已成功部署该服务资源');
          return true;
        } catch (e) {
          message.error(`操作失败:${e}`);
          return false;
        }
      }}
      title={'版本迭代'}
      submitter={{
        searchConfig: {
          submitText: '确认发布',
        },
      }}
      trigger={
        <Button type={'primary'}>
          <RocketOutlined />
        </Button>
      }
      initialValues={{
        repository: lastReleaseSummary.imageRepository,
      }}
    >
      <Alert
        message={`上次发布的标签为:${lastReleaseSummary.imageTag ?? 'latest'}`}
      />
      <ProFormText label={'镜像地址'} name={'repository'} readonly />
      <ProFormText
        label={'发布标签'}
        name={'tag'}
        rules={[{ required: true }, { min: 2 }]}
      />
    </ModalForm>
  );
};

export default RocketForm;
