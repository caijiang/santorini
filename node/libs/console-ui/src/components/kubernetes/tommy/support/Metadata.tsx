import { IIoK8sApimachineryPkgApisMetaV1ObjectMeta } from '@kubernetes-models/apimachinery/apis/meta/v1/ObjectMeta';
import { ProDescriptions } from '@ant-design/pro-components';
import { stringRecordRender } from './renders';

export default ({
  data,
}: {
  data: IIoK8sApimachineryPkgApisMetaV1ObjectMeta;
}) => {
  return (
    <ProDescriptions
      title={'metadata'}
      columns={[
        {
          title: 'namespace',
          dataIndex: 'namespace',
          copyable: true,
        },
        {
          title: 'name',
          dataIndex: 'name',
          copyable: true,
          span: 2,
        },
        {
          //CreationTimestamp is a timestamp representing the server time when this object was created. It is not guaranteed to be set in happens-before order across separate operations. Clients may not set this value. It is represented in RFC3339 form and is in UTC.
          title: 'creationTimestamp',
          dataIndex: 'creationTimestamp',
          valueType: 'dateTime',
          // copyable: true,
          span: 2,
          // @ts-ignore
          filled: true,
        },
        {
          // @ts-ignore
          filled: true,
          title: 'labels',
          dataIndex: 'labels',
          render: (_, entity) => stringRecordRender(entity.labels),
        },
        {
          // @ts-ignore
          filled: true,
          title: 'annotations',
          dataIndex: 'annotations',
          render: (_, entity) => stringRecordRender(entity.annotations),
        },
      ]}
      dataSource={data}
    />
  );
};
