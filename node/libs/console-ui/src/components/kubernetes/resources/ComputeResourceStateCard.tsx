import { ComputeResourceState } from './types';
import { StatisticProps } from 'antd/es/statistic/Statistic';
import { ProgressProps } from 'antd/es/progress/progress';
import { useEffect, useMemo, useState } from 'react';
import { ProCard } from '@ant-design/pro-components';
import { Alert, Col, Progress, Row, Statistic, Typography } from 'antd';

type DisplayColumn = 'used' | 'request' | 'limit';
type ComputeResourceStateCardProps = {
  /**
   * 资源
   */
  state?: ComputeResourceState;
  /**
   * 资源标题
   */
  resourceTitle: string;
  /**
   * 值渲染为统计数据
   * @param amount 值
   */
  toStatisticProps: (amount: number) => StatisticProps;
  /**
   * 警告信息
   */
  toProgressProps: (
    column: DisplayColumn
  ) => ProgressProps & { message?: string };
};

function niceNumber(input: number) {
  return Math.round(input * 10) / 10;
}

/**
 * 显示一项资源
 * @constructor
 */
export function ComputeResourceStateCard({
  state,
  resourceTitle,
  toStatisticProps,
  toProgressProps,
}: ComputeResourceStateCardProps) {
  // 可选择 column
  const allSelectedColumns: DisplayColumn[] = useMemo(() => {
    if (!state) {
      return ['request', 'limit'];
    }
    if (state.used === null || state.used === undefined) {
      return ['request', 'limit'];
    }
    return ['used', 'request', 'limit'];
  }, [state?.used === null || state?.used === undefined]);
  // 当前 column
  const [column, setColumn] = useState(allSelectedColumns[0]);
  const [userSelect, setUserSelect] = useState(false);
  // 每 5 秒自动变更
  useEffect(() => {
    if (userSelect) return undefined;
    let theCounter = 0;
    const of = setInterval(() => {
      theCounter = (theCounter + 1) % allSelectedColumns.length;
      setColumn(allSelectedColumns[theCounter]);
    }, 5000);
    return () => clearInterval(of);
  }, [userSelect, allSelectedColumns, setColumn]);
  const title = useMemo(() => {
    if (column === 'used') return '当前' + resourceTitle;
    if (column === 'request') return '总' + resourceTitle + '请求';
    return '总' + resourceTitle + '限制';
  }, [resourceTitle, column]);
  const columnTitle = useMemo(() => {
    if (column === 'used') return '当前';
    if (column === 'request') return '总请求';
    return '总限制';
  }, [column]);
  const currentProps = useMemo(() => {
    if (!state) return { loading: true };
    if (column === 'used') return toStatisticProps(state.used ?? 0);
    if (column === 'request') return toStatisticProps(state.request ?? 0);
    return toStatisticProps(state.limit ?? 0);
  }, [column, state, toStatisticProps]);
  // 每个 column 有不同的告警标准
  const currentPercent = useMemo(() => {
    if (!state) return undefined;
    if (column === 'used')
      return niceNumber(((state.used ?? 0) * 100) / state.capacity);
    if (column === 'request')
      return niceNumber((state.request * 100) / state.capacity);
    return niceNumber((state.limit * 100) / state.capacity);
  }, [column, state]);
  const { message, ...ps } = toProgressProps(column);

  return (
    <ProCard
      tooltip={
        <Typography.Link
          target={'_blank'}
          href={
            'https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/'
          }
        >
          概念解释
        </Typography.Link>
      }
      style={userSelect ? undefined : { border: '1px dashed #1890ff' }}
      onClick={() => {
        setUserSelect(true);
        const index =
          (allSelectedColumns.indexOf(column) + 1) % allSelectedColumns.length;
        setColumn(allSelectedColumns[index]);
      }}
      title={title}
    >
      <Row gutter={16}>
        <Col span={12}>
          <Statistic {...currentProps} title={columnTitle} />
        </Col>
        <Col span={12}>
          <Statistic
            {...(state ? toStatisticProps(state.capacity) : { loading: true })}
            title="总量"
          />
        </Col>
      </Row>

      <Progress {...ps} percent={currentPercent} showInfo />
      {message && <Alert message={message}></Alert>}
    </ProCard>
  );
}
