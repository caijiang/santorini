import * as React from 'react';
import { PropsWithChildren, useEffect, useMemo } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import {
  hpaLiveSlice,
  OneHpaData,
  selectHpaLiveByService,
} from '../../../slices/hpaLiveSlice';
import { ModuleState } from '../../../c_types';
import dayjs from 'dayjs';
import { Tooltip } from 'antd';
import HpaLiveLine from './HpaLiveLine';

export function useHpaLive(
  envId: string,
  serviceId: string
): OneHpaData | undefined {
  const dispatch = useDispatch();

  useEffect(() => {
    dispatch(hpaLiveSlice.actions.start());
  }, [dispatch]);

  return useSelector((it) =>
    selectHpaLiveByService(it as ModuleState, serviceId, envId)
  );
}

interface HpaLiveLineTooltipContextProps {
  serviceId: string;
  envId: string;
  width?: number;
}

const HpaLiveLineTooltipContext: React.FC<
  PropsWithChildren<HpaLiveLineTooltipContextProps>
> = ({ serviceId, envId, children, width }) => {
  const hpaLiveData = useHpaLive(envId, serviceId);
  const nowDayjs = dayjs();
  const hpaLiveTimeline = useMemo(() => {
    // 当前时间
    // const now = new Date().getTime() / 1000;
    const now = nowDayjs.toDate().getTime() / 1000;
    if (!hpaLiveData) return undefined;
    // 2条线
    return hpaLiveData.timelines
      .filter((it) => {
        return it.time > now - 20 * 60;
      })
      .flatMap((it) => [
        {
          time: it.time,
          value: it.current,
        },
        {
          time: it.time,
          value: hpaLiveData.target,
          target: true,
        },
      ]);
  }, [hpaLiveData, nowDayjs]);
  const useWidth = width || 400;

  return (
    <Tooltip
      open={hpaLiveTimeline && hpaLiveTimeline.length > 0 ? undefined : false}
      title={
        <HpaLiveLine
          hpaLiveData={hpaLiveData}
          hpaLiveTimeline={hpaLiveTimeline}
          width={useWidth}
        />
      }
      styles={{
        body: {
          backgroundColor: '#fff',
          width: useWidth,
        },
      }}
    >
      {children}
    </Tooltip>
  );
};

export default HpaLiveLineTooltipContext;
