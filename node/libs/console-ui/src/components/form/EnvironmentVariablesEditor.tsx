import { useMemo } from 'react';
import _ from 'lodash';
import { ProTable } from '@ant-design/pro-components';
import { Button } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import EnvVarEditor from '../EnvContext/ShareEnv/EnvVarEditor';

export default ({
  value,
  onChange,
}: {
  value?: Record<string, string>;
  onChange?: (v?: Record<string, string>) => void;
}) => {
  const list = useMemo(() => {
    if (!value) return undefined;
    return _.map(value, (value, name) => ({
      name,
      value,
    }));
  }, [value]);

  return (
    <>
      <ProTable
        dataSource={list}
        pagination={false}
        search={false}
        options={false}
        rowKey={'name'}
        columns={[
          {
            dataIndex: 'name',
            title: '名称',
          },
          {
            dataIndex: 'value',
            title: '值',
          },
          {
            valueType: 'option',
            render: (_, entity) => [
              <Button
                key={'delete'}
                danger
                size={'small'}
                onClick={() => {
                  const nv: any = { ...value };
                  delete nv[entity.name];
                  onChange?.(nv);
                }}
              >
                <DeleteOutlined />
              </Button>,
            ],
          },
        ]}
      ></ProTable>
      <EnvVarEditor
        enableSecret={false}
        onFinish={async ({ name, value: v1 }) => {
          const nv: any = { ...value };
          nv[name] = v1;
          onChange?.(nv);
          return true;
        }}
        trigger={
          <Button type={'dashed'}>
            <PlusOutlined />
            新增一个环境变量
          </Button>
        }
      />
    </>
  );
};
