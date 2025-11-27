import { IDeploymentStatus } from 'kubernetes-models/apps/v1/DeploymentStatus';
import { Badge, theme } from 'antd';

function toStatus(availableReplicas: number, unavailableReplicas: number) {
  if (unavailableReplicas == 0 && availableReplicas > 0) return 'success';
  if (unavailableReplicas > 0 && availableReplicas == 0) return 'error';
  if (unavailableReplicas > 0 && availableReplicas > 0) return 'warning';
  return 'processing';
}

export default ({ data }: { data?: IDeploymentStatus }) => {
  const { token } = theme.useToken();
  // <div>
  //   <div style={{ color: token.colorSuccess }}>Success 状态</div>
  //   <div style={{ color: token.colorWarning }}>Warning 状态</div>
  //   <div style={{ color: token.colorError }}>Error 状态</div>
  //   <div style={{ color: token.colorInfo }}>Info 状态</div>
  // </div>
  // 1-1 / 1 // 没有 红色,有 黄色，全 绿色
  const availableReplicas = data?.availableReplicas ?? 0;
  const unavailableReplicas = data?.unavailableReplicas ?? 0;
  const replicas = data?.replicas ?? 1;

  if (!data) {
    return undefined;
  }
  return (
    <Badge dot status={toStatus(availableReplicas, unavailableReplicas)}>
      <span style={{ color: token.colorSuccess }}>{availableReplicas}</span>-
      <span style={{ color: token.colorError }}>{unavailableReplicas}</span>
      &nbsp;&nbsp;/&nbsp;&nbsp;
      <span style={{ color: token.colorInfo }}>{replicas}</span>
    </Badge>
  );
};
