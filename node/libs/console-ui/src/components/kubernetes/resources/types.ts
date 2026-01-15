/**
 * 一项计算资源，单位必须统一！！
 */
export interface ComputeResourceState {
  request: number;
  limit: number;
  /**
   * 不支持统计的话，null or undefined
   */
  used?: number;
  capacity: number;
}

export interface ClusterResourceStat {
  cpu: ComputeResourceState; // millicores
  memory: ComputeResourceState; // Mi
  podsT0: Record<'true' | 'false', number>;
}
