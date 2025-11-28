import _ from 'lodash';
import { ReactNode } from 'react';
import { Button, Popover } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';

type RefreshFunc = () => any;

export interface RefreshAbleProps {
  refresh?: RefreshFunc;
}

export async function invokeRefresh(api: RefreshFunc) {
  const rs1 = api();
  if (rs1 instanceof Promise) {
    await rs1;
  } else {
    const unwrapFunc = rs1['unwrap'];
    if (unwrapFunc && _.isFunction(unwrapFunc)) {
      const rs2 = unwrapFunc.call(rs1);
      if (rs2 instanceof Promise) {
        await rs2;
      }
    }
  }
}

/**
 * 用最简单的方式安装刷新按钮
 * @param child 要展示的功能
 * @param props props
 */
export function wrapperForRefresh(
  child: ReactNode,
  { refresh }: RefreshAbleProps
) {
  if (!refresh) {
    return child;
  }
  return (
    <Popover
      content={
        <Button
          size={'small'}
          type={'dashed'}
          onClick={() => invokeRefresh(refresh)}
        >
          <ReloadOutlined />
        </Button>
      }
    >
      {child}
    </Popover>
  );
}
