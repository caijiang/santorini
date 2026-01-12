import {ServiceConfigData} from '../../../apis/service';
import {NavLink} from 'react-router-dom';
import {useEnvContext} from '../../../layouts/EnvLayout';
import {usePodsQuery} from '../../../apis/kubernetes/pods';
import {useMemo} from 'react';
import {Typography} from 'antd';
import {WarningOutlined} from '@ant-design/icons';

/**
 * 呈现一个按钮，可以引导用户前往观察 pods 的页面
 * @param props
 */
export default ({ service: { id } }: { service: ServiceConfigData }) => {
  const {
    data: { id: envId },
  } = useEnvContext();
  const { data } = usePodsQuery({ namespace: envId });
  // console.log('envId:', envId, ',pods:', data?.length);
  const pods = useMemo(() => {
    return data?.filter(
      (it) => it?.metadata?.labels?.['app.kubernetes.io/name'] == id
    );
  }, [data]);
  // 是否需要关注
  const warn = useMemo(() => {
    // console.log('id:', id, ',pods:', pods);
    return pods
      ?.flatMap((it) => it.status?.containerStatuses ?? [])
      ?.some((it) => {
        return it.restartCount > 0;
      });
  }, [pods]);
  return (
    <NavLink
      title={warn ? '存在崩溃过的容器' : undefined}
      key={'pods'}
      to={`/envFor/${envId}/services/${id}/pods`}
    >
      {warn ? (
        <Typography.Text underline style={{ color: 'darkmagenta' }}>
          <WarningOutlined />
          Pods
        </Typography.Text>
      ) : (
        'Pods'
      )}
    </NavLink>
  );
};
