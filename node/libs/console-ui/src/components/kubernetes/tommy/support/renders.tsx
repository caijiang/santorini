import { Space, Tag } from 'antd';
import _ from 'lodash';

export function stringRecordRender(value?: { [key: string]: string }) {
  if (!value) return undefined;
  return (
    <Space>
      {_.keys(value).map((name) => (
        <Tag key={name}>
          {name}={value[name]}
        </Tag>
      ))}
    </Space>
  );
}
