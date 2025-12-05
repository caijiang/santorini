import { useEffect } from 'react';
import { useEnvContext } from '../../layouts/EnvLayout';
import { useResourcesQuery } from '../../apis/env';
import { useParams } from 'react-router-dom';
import { ProDescriptions } from '@ant-design/pro-components';
import {
  resourceDescriptionColumn,
  resourceNameColumn,
  resourceTypeColumn,
  resourceTypeToColumns,
} from '../../columns/env_resource';

export default () => {
  const { data, setSharePageContainerProps } = useEnvContext();
  const { name } = useParams();
  const { data: list, isLoading } = useResourcesQuery({
    envId: data.id,
    params: { name },
  });
  const detail = list?.find(() => true);
  // console.log('detail:', detail, ',isLoading:', isLoading);
  // useEffect(() => {
  //   setSharePageContainerProps({
  //     loading: isLoading,
  //     title: detail ? detail.name : '资源详情',
  //   });
  // }, [list, !!isLoading]);
  useEffect(() => {
    setSharePageContainerProps({ title: '资源详情' });
  }, []);
  return (
    <>
      <ProDescriptions
        loading={isLoading}
        dataSource={detail}
        columns={[
          resourceTypeColumn,
          resourceNameColumn,
          resourceDescriptionColumn,
          ...(detail?.type ? resourceTypeToColumns(detail.type) : []),
        ]}
      ></ProDescriptions>
    </>
  );
};
