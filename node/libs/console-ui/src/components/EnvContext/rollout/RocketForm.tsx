import { ExclamationCircleOutlined, RocketOutlined } from '@ant-design/icons';
import {
  ModalForm,
  ProFormDependency,
  ProFormSwitch,
  ProFormText,
} from '@ant-design/pro-components';
import { ServiceConfigData, useServiceByIdQuery } from '../../../apis/service';
import { Alert, App, Button, Spin, Typography } from 'antd';
import * as React from 'react';
import { useEnvContext } from '../../../layouts/EnvLayout';
import {
  deployToKubernetes,
  useImageTagRule,
} from '../../../slices/deployService';
import { dispatchAsyncThunkActionThrowIfError } from '../../../common/rtk';
import { useDispatch } from 'react-redux';
import { useLastReleaseQuery } from '../../../apis/deployment';
import PreAuthorize from '../../../tor/PreAuthorize';

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
  const tagRule = useImageTagRule(serviceId, env.id, lastReleaseSummary);
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
          await dispatchAsyncThunkActionThrowIfError(dispatch, action);
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
      <ProFormText label={'镜像地址'} name={'repository'} readonly />
      <ProFormDependency name={['experiment']}>
        {({ experiment }) => (
          <ProFormText
            label={'发布标签'}
            name={'tag'}
            rules={
              experiment
                ? [{ required: true }, { min: 2 }]
                : [{ required: true }, { min: 2 }, tagRule]
            }
          />
        )}
      </ProFormDependency>
      <Alert
        message={
          <Typography>
            <Typography.Text strong underline>
              绝对
            </Typography.Text>
            不会重新拉取已存在的镜像标签！
          </Typography>
        }
        type={'warning'}
        showIcon
      />
    </ModalForm>
  );
};

export default RocketForm;
