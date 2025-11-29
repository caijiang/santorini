import { NginxIngressAnnotation } from './IngressAnnotation';
import { Button, Input, Space, Tag, Tooltip } from 'antd';
import * as React from 'react';
import { useState } from 'react';
import { EditOutlined, PlusOutlined } from '@ant-design/icons';

// import { TweenOneGroup } from 'rc-tween-one';

interface NginxIngressAnnotationsInputProps {
  value?: NginxIngressAnnotation[];
  onChange?: (value: NginxIngressAnnotation[]) => any;
}

const NginxIngressAnnotationsInput: React.FC<
  NginxIngressAnnotationsInputProps
> = ({ value, onChange }) => {
  // 编辑,删除，新增
  // https://ant.design/components/tag-cn#tag-demo-control
  const list = value ?? [];
  const [editType, setEditType] = useState<'edit' | 'new'>();
  const [editName, setEditName] = useState<string>();
  const [editValue, setEditValue] = useState<string>();
  return (
    <Space>
      {list.map((it) =>
        // 编辑时给 input 其他给 tag
        editType == 'edit' && editName == it.name ? (
          <Input
            key={it.name}
            allowClear
            onClear={() => {
              setEditType(undefined);
            }}
            value={editValue}
            // @ts-ignore
            onChange={({ target }) => setEditValue(target.value)}
            onPressEnter={() => {
              if (!editValue || editValue.length == 0) return;
              onChange?.(
                list.map((that) =>
                  that.name == it.name ? { ...that, value: editValue } : that
                )
              );
              setEditType(undefined);
            }}
          />
        ) : (
          <Tag
            key={it.name}
            closable
            onClose={() => {
              onChange?.(list.filter((that) => that.name != it.name));
            }}
          >
            {!editType ? (
              <Tooltip
                title={
                  <Button
                    size={'small'}
                    type={'dashed'}
                    onClick={() => {
                      setEditName(it.name);
                      setEditValue(it.value);
                      setEditType('edit');
                    }}
                  >
                    <EditOutlined />
                  </Button>
                }
              >
                {it.name}:{it.value}
              </Tooltip>
            ) : (
              <span>
                {it.name}:{it.value}
              </span>
            )}
          </Tag>
        )
      )}
      {editType == 'new' && (
        <>
          <Input
            value={editName}
            // @ts-ignore
            onChange={({ target }) => setEditName(target.value)}
            suffix={':'}
          />
          <Input
            value={editValue}
            // @ts-ignore
            onChange={({ target }) => setEditValue(target.value)}
            onPressEnter={() => {
              if (!editName || !editValue) return;
              if (editName.length == 0 || editValue.length == 0) return;
              onChange?.([
                ...list.filter((that) => that.name != editName),
                {
                  name: editName,
                  value: editValue,
                },
              ]);
              setEditType(undefined);
            }}
          />
        </>
      )}
      {!editType && (
        <Button
          type={'dashed'}
          size={'small'}
          onClick={() => {
            setEditName(undefined);
            setEditValue(undefined);
            setEditType('new');
          }}
        >
          <PlusOutlined />
        </Button>
      )}
    </Space>
  );
};

export default NginxIngressAnnotationsInput;
