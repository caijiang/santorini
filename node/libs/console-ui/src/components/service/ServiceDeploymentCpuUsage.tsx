import { ServiceDeployment } from './types';
import { useDeploymentQuery } from '../../apis/kubernetes/service';
import { IDeployment } from 'kubernetes-models/apps/v1/Deployment';
import { useMemo } from 'react';
import { IQuantity } from '@kubernetes-models/apimachinery/api/resource/Quantity';
import _ from 'lodash';

export function queryResourceUsageFromDeployment(
  deployment: IDeployment | undefined
) {
  const containers = deployment?.spec?.template.spec?.containers;
  const container =
    containers?.find((it) => it.name == 'main') ??
    (containers && containers.length > 0 && containers[0]);

  if (!container) return undefined;
  return container.resources;
}

export function resourceQuantityString(
  amount: IQuantity,
  type: 'cpu' | 'memory'
) {
  // https://kubernetes.io/zh-cn/docs/reference/kubernetes-api/common-definitions/quantity/#Quantity
  if (_.isNumber(amount) && type === 'cpu') {
    return `${amount}核`;
  }
  try {
    // console.log('amount:', amount, ',type:', typeof amount,',,,r:',/^[\d.]+$/.test(amount));
    if (_.isString(amount) && /^[\d.]+$/.test(amount)) {
      // 全是 数字和 .
      // const number = parseFloat(amount);
      return `${amount}核`;
    }
  } catch (e) {}
  if (_.isString(amount)) return amount;
  return `${amount}`;
}

export function resourceUsage(
  request: IQuantity | undefined,
  limit: IQuantity | undefined,
  type: 'cpu' | 'memory'
) {
  if (request === undefined && limit === undefined) return undefined;
  if (request === undefined) return `~${resourceQuantityString(limit!!, type)}`;
  if (limit === undefined) return `${resourceQuantityString(request, type)}~`;
  if (request == limit) return resourceQuantityString(request, type);
  return `${resourceQuantityString(request, type)}-${resourceQuantityString(
    limit,
    type
  )}`;
}

export default ({ service: { id }, envId }: ServiceDeployment) => {
  const { data } = useDeploymentQuery({
    namespace: envId,
    name: id,
    labelSelectors: ['santorini.io/service-type', `santorini.io/id=${id}`],
  });
  return useMemo(() => {
    const ir = queryResourceUsageFromDeployment(data);
    if (!ir) return undefined;
    const request = ir.requests?.['cpu'];
    const limit = ir.limits?.['cpu'];
    return resourceUsage(request, limit, 'cpu');
  }, [data]);
};
