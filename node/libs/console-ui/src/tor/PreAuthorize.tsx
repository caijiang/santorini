import * as React from 'react';
import { ReactNode, useMemo } from 'react';
import { useCurrentLoginUserQuery } from '@private-everest/app-support';
import { Button, Result, Skeleton, Spin } from 'antd';
import { common } from '@private-everest/generated-common';
import _, { upperCase } from 'lodash';
import { NavLink } from 'react-router-dom';

interface PreAuthorizeProps {
  /**
   * 内容类型,默认会认为是片段
   */
  childrenType?: 'page' | 'fragment';
  onLoading?: ReactNode;
  onFallback?: ReactNode;
  /**
   * 最高优先级的权限判断
   * @param user
   */
  value?: (user: common.LoginUser) => boolean;
  /**
   * 符合任意 role 即可,role跟 Authority 并不一致
   */
  haveAnyRole?: string[] | string;
}

export function useCurrentLoginUserHaveAnyRole(haveAnyRole: string[] | string) {
  const { data } = useCurrentLoginUserQuery(undefined);

  if (!data) return false;
  return beGranted(data, haveAnyRole);
}

function beGranted(data: common.LoginUser, haveAnyRole: string[] | string) {
  return data.grantAuthorities.some((authority) =>
    _.isArray(haveAnyRole)
      ? haveAnyRole.some(
          (it) => upperCase(authority) == upperCase(`ROLE_${it}`)
        )
      : upperCase(authority) == upperCase(`ROLE_${haveAnyRole}`)
  );
}

const PreAuthorize: React.FC<React.PropsWithChildren<PreAuthorizeProps>> = ({
  haveAnyRole,
  value,
  childrenType,
  onLoading,
  onFallback,
  children,
}) => {
  const { data, isLoading } = useCurrentLoginUserQuery(undefined);
  const grant = useMemo(() => {
    if (!data) return false;
    if (value) return value(data);
    if (haveAnyRole) {
      return beGranted(data, haveAnyRole);
    }
    console.warn('既没有声明 haveAnyRole 也没有声明 value');
    return false;
  }, [data, haveAnyRole, value]);

  if (isLoading) {
    if (onLoading) return onLoading;
    if (childrenType == 'page') {
      return <Skeleton />;
    }
    return <Spin />;
  }
  if (!grant) {
    if (onFallback) return onFallback;
    if (childrenType == 'page') {
      return (
        <Result
          status="403"
          title="403"
          subTitle="Sorry, you are not be granted to visit this page."
          extra={
            <NavLink to={'/'}>
              <Button type="primary">返回首页</Button>
            </NavLink>
          }
        />
      );
    }
    return null;
  }
  return children;
};

export default PreAuthorize;
