import { humanReadMessage, OneHpaData } from '../../../slices/hpaLiveSlice';
import * as React from 'react';
import { Line, LineConfig } from '@ant-design/plots';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

interface HpaLiveLineData {
  time: number;
  value: number;
  target?: boolean;
}

interface HpaLiveLineProps {
  hpaLiveData?: OneHpaData;
  hpaLiveTimeline?: HpaLiveLineData[];
}

dayjs.extend(relativeTime);

const HpaLiveLine: React.FC<HpaLiveLineProps & LineConfig> = ({
  hpaLiveTimeline,
  hpaLiveData,
  ...props
}) => {
  return (
    <Line
      width={400}
      {...props}
      data={hpaLiveTimeline}
      // xField={(x: any) => x.time}
      xField={'time'}
      yField={'value'}
      colorField={(it: HpaLiveLineData) => {
        if (it.target) return '目标';
        return '当时';
      }}
      tooltip={{
        title: (d: HpaLiveLineData) => {
          const instant = dayjs(d.time * 1000);
          return instant.format('YYYY-MM-DD HH:mm:ss');
        },
      }}
      axis={{
        y: {
          labelFormatter: (input: number) => {
            if (!hpaLiveData) return undefined;
            return humanReadMessage(hpaLiveData, input);
          },
        },
        x: {
          labelFormatter: (input: number) => {
            const d = dayjs(input * 1000);
            return d.toNow();
          },
        },
      }}
    />
  );
};

export default HpaLiveLine;
