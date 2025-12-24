import dayjs, { Dayjs } from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import { useMemo } from 'react';
import _ from 'lodash';
import { Tooltip } from 'antd';

dayjs.extend(relativeTime);

type DatetimeProps = {
  /**
   * 当前时区
   */
  value: any;
};

function printDatetime(instant: Dayjs, now: Dayjs) {
  const diff = now.diff(instant, 'd');
  if (diff == 0) return instant.format('HH:mm:ss');
  return instant.format('YYYY-MM-DD HH:mm:ss'); //.SSS
}

/**
 * 渲染的是当前时区的
 * @param props
 */
export default ({ value }: DatetimeProps) => {
  const instant = useMemo(() => {
    if (!value) return undefined;
    if (!_.isString(value)) return undefined;
    // 只保留前 3 位毫秒
    const normalized = value.replace(/(\.\d{3})\d+$/, '$1');

    return dayjs(normalized);
  }, [value]);
  const now = dayjs();

  if (!instant) {
    return undefined;
  }
  return (
    <Tooltip title={`${instant.fromNow()}`}>
      {printDatetime(instant, now)}
    </Tooltip>
  );
};
