import { ServiceDeployment } from './types';
import { useDeploymentQuery } from '../../apis/kubernetes/service';
import { useMemo } from 'react';
import {
  queryResourceUsageFromDeployment,
  resourceUsage,
} from './ServiceDeploymentCpuUsage';

export default ({ service: { id }, envId }: ServiceDeployment) => {
  const { data } = useDeploymentQuery({
    namespace: envId,
    name: id,
    labelSelectors: ['santorini.io/service-type', `santorini.io/id=${id}`],
  });
  return useMemo(() => {
    const ir = queryResourceUsageFromDeployment(data);
    if (!ir) return undefined;
    const request = ir.requests?.['memory'];
    const limit = ir.limits?.['memory'];
    return resourceUsage(request, limit, 'memory');
  }, [data]);
};
