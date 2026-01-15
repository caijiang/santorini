import { ClusterResourceStat } from './types';
import { Col, Row } from 'antd';
import { CpuStatCard } from './CpuStatCard';
import { MemoryStatCard } from './MemoryStatCard';

export default ({ state }: { state?: ClusterResourceStat }) => {
  return (
    <Row gutter={16}>
      <Col xs={12} sm={12} md={12} lg={10} xl={8} xxl={6}>
        <CpuStatCard cpu={state?.cpu} />
      </Col>
      <Col xs={12} sm={12} md={12} lg={10} xl={8} xxl={6}>
        <MemoryStatCard memory={state?.memory} />
      </Col>
    </Row>
  );
};
