import { CUEnv } from '../../apis/env';
import { Space, Tag } from 'antd';
import * as React from 'react';

interface EnvProps {
  data: CUEnv;
}

const EnvSubTitle: React.FC<EnvProps> = ({ data: e }) => {
  return (
    <Space size={0}>
      <Tag>{e.id}</Tag> {e.production && <Tag color={'red'}>生产</Tag>}
    </Space>
  );
};

const Env: React.FC<EnvProps> & {
  SubTitle: typeof EnvSubTitle;
} = ({ data }) => {
  return (
    <Space>
      {data.name}
      <EnvSubTitle data={data} />
    </Space>
  );
};

Env.SubTitle = EnvSubTitle;
//
// type CompoundedComponent = typeof Env & {
//   SubTitle: typeof EnvSubTitle;
// };
// declare const Splitter: CompoundedComponent;
// export default Splitter;
//
// export default EnvChooserModal;
//

export default Env;
