import * as React from 'react';
import { useEffect, useMemo, useState } from 'react';
import { useDeploymentHistorySummaryQuery } from '../../apis/deployment';
import { Empty, Skeleton, Timeline, Typography } from 'antd';
import Datetime from '../common/Datetime';
import OneUser from '../common/OneUser';
import { useEnvs } from '../../hooks/common';
import Env from '../env/Env';

interface DeploymentHistoryProps {
  serviceId: string;
  envId?: string;
}

/**
 * 展示发布记录
 * @constructor
 */
const DeploymentHistory: React.FC<DeploymentHistoryProps> = (props) => {
  const envs = useEnvs();
  const [pollingInterval, setPollingInterval] = useState(60 * 60 * 1000);
  const { data: ds, isLoading } = useDeploymentHistorySummaryQuery(props, {
    // refetchOnFocus:true,
    pollingInterval: pollingInterval,
  });
  const data = useMemo(() => {
    if (!envs) return undefined;
    return ds?.filter((d) => envs.some((it) => it.id == d.env));
  }, [ds, envs]);

  useEffect(() => {
    if (data?.some((it) => !it.completed && !it.expiredVersion) === true) {
      setPollingInterval(6000);
    } else {
      setPollingInterval(60 * 60 * 1000);
    }
  }, [data]);

  if (isLoading || !envs) {
    return <Skeleton />;
  }
  if (!data || data.length == 0) {
    return <Empty />;
  }
  return (
    <Timeline
      mode={'left'}
      items={data.map((it) => ({
        key: it.createTime,
        // title: 'title', 会渲染
        // content: 'content',
        color: it.successful
          ? 'green'
          : it.completed
          ? 'red'
          : it.expiredVersion
          ? 'gray'
          : 'blue',
        label: (
          <>
            <OneUser data={it.operator} /> 于<Datetime value={it.createTime} />
            部署到
            <Env data={envs.find((e) => e.id == it.env)!!} />
          </>
        ),
        children: (
          <Typography.Paragraph>
            <Typography.Text copyable ellipsis>
              {it.imageRepository}
            </Typography.Text>
            :
            <Typography.Text copyable>
              {it.imageTag ?? 'latest'}
            </Typography.Text>
          </Typography.Paragraph>
        ),
      }))}
    ></Timeline>
  );
};

export default DeploymentHistory;
