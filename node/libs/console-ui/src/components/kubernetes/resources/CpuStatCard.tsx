import {ComputeResourceState} from './types';
import {ComputeResourceStateCard} from './ComputeResourceStateCard';

/**
 * Request / Capacity > 100% → 红色(90) 警告 70
 * Limit / Capacity > 200% → Warning(150)
 *
 * Used / Capacity > 50% 危险
 * Used > Request → 应用压力 / 资源不足
 * @param cpu
 * @constructor
 */
export function CpuStatCard({ cpu }: { cpu?: ComputeResourceState }) {
  // 虚标 -> 当前,请求,限制 自动循环，若用户点击则为实标
  return (
    <ComputeResourceStateCard
      state={cpu}
      resourceTitle="CPU"
      toStatisticProps={(amount) => ({
        value: amount / 1000,
        suffix: '核',
        precision: 1,
      })}
      toProgressProps={(column) => {
        if (!cpu) {
          return {
            loading: true,
          };
        }
        if (column === 'used') {
          if ((cpu.used ?? 0) > cpu.capacity / 2)
            return {
              message: '接近集群总可用值，尽快扩充节点',
              status: 'exception' as 'exception',
            };
          if ((cpu.used ?? 0) > cpu.request)
            return {
              message: '部分服务存在工作压力，需要扩充服务实例',
              status: 'normal' as `normal`,
              strokeColor: '#faad14',
            };
          return {
            status: 'active' as 'active',
          };
        }
        if (column === 'request') {
          if (cpu.request > cpu.capacity * 0.9)
            return {
              message: '请求值接近集群总可用值，集群将无法调度更多实例',
              status: 'exception',
            };
          if (cpu.request > cpu.capacity * 0.7)
            return {
              message: '请求值接近集群总可用值，尽快扩充节点',
              status: 'normal' as `normal`,
              strokeColor: '#faad14',
            };
          return {
            status: 'active',
          };
        }
        if (cpu.limit > cpu.capacity * 2.0)
          return {
            message: '总限制值已经远超集群可用值',
            status: 'exception',
          };
        if (cpu.limit > cpu.capacity * 1.5)
          return {
            message: '总限制值已经远超集群可用值',
            status: 'normal' as `normal`,
            strokeColor: '#faad14',
          };
        return {
          status: 'active',
        };
      }}
    />
  );
  // return (
  //   <ProCard title="CPU">
  //     <Row gutter={16}>
  //       <Col span={6}>
  //         <Statistic
  //           title="Used"
  //           value={cpu.used / 1000}
  //           suffix="核"
  //           precision={1}
  //         />
  //       </Col>
  //       <Col span={6}>
  //         <Statistic
  //           title="Request"
  //           value={cpu.request / 1000}
  //           suffix="核"
  //           precision={1}
  //         />
  //       </Col>
  //       <Col span={6}>
  //         <Statistic
  //           title="Limit"
  //           value={cpu.limit / 1000}
  //           suffix="核"
  //           precision={1}
  //         />
  //       </Col>
  //       <Col span={6}>
  //         <Statistic
  //           title="capacity"
  //           value={cpu.capacity / 1000}
  //           suffix="核"
  //           precision={1}
  //         />
  //       </Col>
  //     </Row>
  //
  //     <Progress
  //       percent={usedPercent}
  //       status={usedPercent > 80 ? 'exception' : 'active'}
  //       showInfo
  //     />
  //   </ProCard>
  // );
}
