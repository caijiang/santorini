// noinspection JSUnusedGlobalSymbols

import { ClusterResourceStat } from './types';
import { ProCard } from '@ant-design/pro-components';
import { Progress } from 'antd';

export default ({}: { data?: ClusterResourceStat['podsT0'] }) => {
  return (
    <ProCard title={'Pod'}>
      <Progress type={'circle'} percent={1} />
    </ProCard>
  );
};
